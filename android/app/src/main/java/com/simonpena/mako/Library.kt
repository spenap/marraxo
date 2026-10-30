package com.simonpena.mako

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaScannerConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val number: Int,
    val uri: Uri,
)

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val tracks: List<Track>,
    val genre: String = "",
)

/** Artists sort ignoring a leading "The", so The Strokes is under S. */
private fun sortKey(artist: String) = artist.removePrefix("The ").lowercase()

private fun clean(value: String?, fallback: String) =
    value?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING } ?: fallback

/**
 * Only audio under the shared Music folder is part of the library; test
 * tones, recordings and ringtones elsewhere on the phone are ignored.
 */
@Suppress("DEPRECATION") // DATA and the public directory path are the only way on API 27–28
private fun musicFolderSelection(): Pair<String, Array<String>> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?" to arrayOf("${Environment.DIRECTORY_MUSIC}/%")
    } else {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
        "${MediaStore.Audio.Media.DATA} LIKE ?" to arrayOf("$root/%")
    }

/** Reads the audio files in the Music folder from Android's MediaStore, grouped into albums. */
suspend fun loadLibrary(context: Context): List<Album> = withContext(Dispatchers.IO) {
    val m = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.YEAR,
    )
    val tracks = mutableListOf<Track>()
    val years = mutableMapOf<Long, Int>()
    val (folder, args) = musicFolderSelection()
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND $folder"
    context.contentResolver.query(m, projection, selection, args, null)?.use { c ->
        val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val track = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val year = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        while (c.moveToNext()) {
            val trackId = c.getLong(id)
            val aId = c.getLong(albumId)
            tracks += Track(
                id = trackId,
                title = clean(c.getString(title), "Unknown track"),
                artist = clean(c.getString(artist), "Unknown artist"),
                album = clean(c.getString(album), "Unknown album"),
                albumId = aId,
                durationMs = c.getLong(duration),
                number = c.getInt(track) % 1000, // stored as disc * 1000 + track
                uri = ContentUris.withAppendedId(m, trackId),
            )
            if (!c.isNull(year)) years[aId] = c.getInt(year)
        }
    }
    val genres = trackGenres(context)
    tracks.groupBy { it.albumId }
        .map { (albumId, list) ->
            val sorted = list.sortedWith(compareBy({ it.number }, { it.title }))
            val first = sorted.first()
            // The genre most of the album's songs share.
            val genre = list.mapNotNull { genres[it.id] }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
            Album(albumId, first.album, first.artist, years[albumId] ?: 0, sorted, genre)
        }
        .sortedWith(compareBy({ sortKey(it.artist) }, { it.year }, { it.title }))
}

/** Track id → genre. Android 8 keeps genres in their own table, one query per genre. */
private fun trackGenres(context: Context): Map<Long, String> {
    val result = HashMap<Long, String>()
    val resolver = context.contentResolver
    resolver.query(
        MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME), null, null, null,
    )?.use { g ->
        while (g.moveToNext()) {
            val name = g.getString(1)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            resolver.query(
                MediaStore.Audio.Genres.Members.getContentUri("external", g.getLong(0)),
                arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID), null, null, null,
            )?.use { m -> while (m.moveToNext()) result[m.getLong(0)] = name }
        }
    }
    return result
}

private val AUDIO = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav", "mpc")

/** What a rescan changed, for the message shown afterwards. */
data class ScanResult(val added: Int, val removed: Int)

/**
 * Brings Android's media index up to date with the Music folder: files
 * pushed over ADB or USB are not always indexed, and deleted files can
 * linger. Only the differences are scanned; scanning a path that no longer
 * exists removes it from the index.
 */
@Suppress("DEPRECATION") // DATA: the file path, still readable on every API level
suspend fun rescanMusicFolder(context: Context): ScanResult = withContext(Dispatchers.IO) {
    val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
    val onDisk = root.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() in AUDIO }
        .map(File::getAbsolutePath)
        .toSet()
    val indexed = mutableSetOf<String>()
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media.DATA),
        "${MediaStore.Audio.Media.DATA} LIKE ?", arrayOf("${root.absolutePath}/%"), null,
    )?.use { c -> while (c.moveToNext()) c.getString(0)?.let(indexed::add) }
    val added = onDisk - indexed
    val removed = indexed - onDisk
    val paths = (added + removed).toTypedArray()
    if (paths.isNotEmpty()) {
        val done = CompletableDeferred<Unit>()
        val left = AtomicInteger(paths.size)
        MediaScannerConnection.scanFile(context, paths, null) { _, _ ->
            if (left.decrementAndGet() == 0) done.complete(Unit)
        }
        withTimeoutOrNull(120_000) { done.await() } // never hang the UI on a lost callback
    }
    ScanResult(added.size, removed.size)
}
