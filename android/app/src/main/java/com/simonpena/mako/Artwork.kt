package com.simonpena.mako

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/** Album artwork decoded at the size it's shown, plus the Glow tint picked from it. */
class Art(val image: ImageBitmap, val tint: Color)

private val cache = LruCache<String, Art>(48)

/** Albums without artwork, so genre tiles looking for a cover don't ask again on every visit. */
private val noArt: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()

/**
 * At most two artwork decodes at a time. Dispatchers.IO alone allows 64, and
 * on the Nexus 4's four cores they starve the UI thread while scrolling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private val artDispatcher = Dispatchers.IO.limitedParallelism(2)

/** Small images (grid tiles, mini player) don't need the Glow tint. */
private const val TINT_MIN_PX = 400

/** Fallback tints for albums without artwork, so the grid isn't uniform. */
private val placeholders = listOf(0xFF24525A, 0xFF5E2238, 0xFF7A4F1E, 0xFF2D455C, 0xFF3F474E, 0xFF5C4A2A)
    .map { Color(it) }

fun placeholderTint(albumId: Long): Color = placeholders[(albumId % placeholders.size).toInt().let { if (it < 0) -it else it }]

private fun albumArtUri(albumId: Long): Uri =
    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

/** Only for the notification on Android 8–9, where Media3 can load this URI itself. */
fun legacyArtworkUri(albumId: Long): Uri? =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) albumArtUri(albumId) else null

private fun decodeSampled(context: Context, uri: Uri, px: Int): Bitmap? =
    decodeSampled(px) { context.contentResolver.openInputStream(uri) }

private fun decodeSampled(px: Int, open: () -> java.io.InputStream?): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= px) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        if (px < TINT_MIN_PX) inPreferredConfig = Bitmap.Config.RGB_565 // half the memory, faster upload
    }
    return open()?.use { BitmapFactory.decodeStream(it, null, opts) }
}

/** The first of [sources] that gives art; one that fails or throws counts as none. */
fun <T> firstArt(vararg sources: () -> T?): T? = sources.firstNotNullOfOrNull { runCatching(it).getOrNull() }

/**
 * An album's cover: Android's thumbnail, or else the picture embedded in
 * its first track. Android's can be missing or stale (on the Nexus 4, The
 * Blue Idol's album art entry pointed to a deleted file).
 */
private fun coverBitmap(context: Context, albumId: Long, px: Int): Bitmap? = firstArt(
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId), Size(px, px), null)
        } else {
            decodeSampled(context, albumArtUri(albumId), px)
        }
    },
    { embeddedCover(context, albumId, px) },
)

private fun embeddedCover(context: Context, albumId: Long, px: Int): Bitmap? {
    val track = context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media._ID),
        "${MediaStore.Audio.Media.ALBUM_ID} = ?", arrayOf(albumId.toString()), null,
    )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0)) else null }
        ?: return null
    val bytes = MediaMetadataRetriever().run {
        try {
            setDataSource(context, track)
            embeddedPicture
        } finally {
            release()
        }
    } ?: return null
    return decodeSampled(px) { bytes.inputStream() }
}

/** [tint]: pick the Glow colour too; small images skip it unless they're shown on it (genre tiles). */
suspend fun loadArt(context: Context, albumId: Long, px: Int, tint: Boolean = px >= TINT_MIN_PX): Art? = withContext(artDispatcher) {
    val key = artKey(albumId, px, tint)
    if (albumId in noArt) return@withContext null
    cache.get(key) ?: runCatching {
        val bitmap = coverBitmap(context, albumId, px) ?: run { noArt += albumId; return@runCatching null }
        val fallback = placeholderTint(albumId).toArgb()
        val color = if (!tint) fallback else {
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            palette.getDarkVibrantColor(palette.getDarkMutedColor(palette.getDominantColor(fallback)))
        }
        Art(bitmap.asImageBitmap(), Color(color))
    }.getOrNull()?.also { cache.put(key, it) }
}

private fun artKey(albumId: Long, px: Int, tint: Boolean) = "$albumId@$px" + if (tint && px < TINT_MIN_PX) "t" else ""

/** True once loading this album's artwork has found none. */
fun knownWithoutArt(albumId: Long) = albumId in noArt

/** Artwork already decoded, for a first frame without a flash of the placeholder. */
fun cachedArt(albumId: Long, px: Int, tint: Boolean): Art? = cache.get(artKey(albumId, px, tint))

/** Cover as JPEG bytes, for renderers that show artwork (the TV). Blocking. */
fun coverJpeg(context: Context, albumId: Long, px: Int = 500): ByteArray? = runCatching {
    val bitmap = coverBitmap(context, albumId, px) ?: return@runCatching null
    java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
}.getOrNull()

@Composable
fun rememberArt(albumId: Long, px: Int): Art? {
    val context = LocalContext.current
    val art by produceState(cache.get(artKey(albumId, px, px >= TINT_MIN_PX)), albumId, px) {
        value = loadArt(context, albumId, px)
    }
    return art
}

/** Square artwork. [px] is the decode size; the modifier sets the on-screen size. */
@Composable
fun AlbumArt(albumId: Long, px: Int, corner: Dp, modifier: Modifier = Modifier) {
    val art = rememberArt(albumId, px)
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(art?.tint ?: placeholderTint(albumId)),
    ) {
        if (art != null) {
            Image(art.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                GlowIcons.Music, contentDescription = null, tint = Color.White.copy(alpha = .35f),
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(.28f),
            )
        }
    }
}
