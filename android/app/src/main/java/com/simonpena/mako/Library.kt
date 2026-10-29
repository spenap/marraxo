package com.simonpena.mako

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
)

/** Artists sort ignoring a leading "The", so The Strokes is under S. */
private fun sortKey(artist: String) = artist.removePrefix("The ").lowercase()

private fun clean(value: String?, fallback: String) =
    value?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING } ?: fallback

/** Reads the audio files Android's MediaStore knows about, grouped into albums. */
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
    context.contentResolver.query(m, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { c ->
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
    tracks.groupBy { it.albumId }
        .map { (albumId, list) ->
            val sorted = list.sortedWith(compareBy({ it.number }, { it.title }))
            val first = sorted.first()
            Album(albumId, first.album, first.artist, years[albumId] ?: 0, sorted)
        }
        .sortedWith(compareBy({ sortKey(it.artist) }, { it.year }, { it.title }))
}
