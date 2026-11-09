package com.simonpena.marraxo

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/*
 * Audio files opened from another app ("Open with"). They replace the queue
 * and aren't added to the library. A file MediaStore knows plays by its
 * MediaStore URI, so the library and play history know it. Any other file
 * is copied to the cache first: the sender's grant ends when the screen it
 * opened closes, and seeking or a speaker would then fail.
 */

data class Tags(val title: String, val artist: String, val album: String, val durationMs: Long, val albumId: Long? = null)

/** The MediaStore id in a MediaStore audio URI (content://media/external/audio/media/42), else null. */
internal fun mediaStoreId(uri: Uri): Long? {
    val s = uri.pathSegments
    if (uri.scheme != "content" || uri.authority != MediaStore.AUTHORITY || s.size != 4 || s[1] != "audio" || s[2] != "media") return null
    return s[3].toLongOrNull()
}

/** "Voice note 3.m4a" → "Voice note 3". */
internal fun titleFromName(name: String) = name.substringBeforeLast('.').ifEmpty { name }

/** The queue item for [uri]: a MediaStore track by its id, any other file by its URI. */
internal fun openedItem(uri: Uri, tags: Tags): MediaItem = MediaItem.Builder()
    .setMediaId(mediaStoreId(uri)?.toString() ?: uri.toString())
    .setUri(uri)
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(tags.title)
            .setArtist(tags.artist.ifBlank { null })
            .setAlbumTitle(tags.album.ifBlank { null })
            .setDurationMs(tags.durationMs.takeIf { it > 0 })
            .setExtras(tags.albumId?.let { id -> Bundle().apply { putLong(EXTRA_ALBUM_ID, id) } })
            .build()
    )
    .build()

/** Reads what's needed to play [uri]; null if it can't be read. */
suspend fun readOpenedFile(context: Context, uri: Uri): MediaItem? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val (name, size) = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) to (if (c.isNull(1)) -1L else c.getLong(1)) else null
        }
    }.getOrNull() ?: ((uri.lastPathSegment ?: "") to -1L)
    val stored = if (mediaStoreId(uri) != null) uri else inMediaStore(context, name, size)
    val playUri = stored ?: runCatching {
        resolver.openInputStream(uri)!!.use { Uri.fromFile(keepCopy(it, File(context.cacheDir, "opened"), name)) }
    }.getOrNull() ?: return@withContext null
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, playUri)
        val tag = { key: Int -> retriever.extractMetadata(key)?.trim()?.let(::repairMojibake).orEmpty() }
        val tags = Tags(
            title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE).ifEmpty { titleFromName(name) },
            artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST),
            album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
            durationMs = tag(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0,
            albumId = stored?.let { albumIdOf(context, it) },
        )
        openedItem(playUri, tags)
    } catch (e: Exception) {
        null
    } finally {
        retriever.release()
    }
}

/** Copies [input] into [dir] as [name], deleting what was there: only the file playing is kept. */
internal fun keepCopy(input: InputStream, dir: File, name: String): File {
    dir.mkdirs()
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, File(name).name.ifEmpty { "opened" })
    file.outputStream().use { input.copyTo(it) }
    return file
}

private fun albumIdOf(context: Context, uri: Uri): Long? = runCatching {
    context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.ALBUM_ID), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getLong(0) else null
    }
}.getOrNull()

/** The one MediaStore audio file with this name and size, if MediaStore has it and it can be read. */
private fun inMediaStore(context: Context, name: String, size: Long): Uri? = runCatching {
    if (name.isEmpty() || size < 0) return null
    val audio = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    context.contentResolver.query(
        audio, arrayOf(MediaStore.Audio.Media._ID),
        "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.SIZE} = ?", arrayOf(name, size.toString()), null,
    )?.use { c -> if (c.count == 1 && c.moveToFirst()) ContentUris.withAppendedId(audio, c.getLong(0)) else null }
        ?.takeIf { found -> context.contentResolver.openFileDescriptor(found, "r")?.use { true } == true }
}.getOrNull()
