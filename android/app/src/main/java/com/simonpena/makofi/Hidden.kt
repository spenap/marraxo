package com.simonpena.makofi

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Albums and songs hidden from the library. Files are never touched;
 * everything hidden can be shown again from ⋮ → Hidden.
 *
 * Keyed by artist, album and title, not MediaStore ids, which change when
 * files are rescanned, moved or rotated onto the phone again.
 */
class Hidden(context: Context) {
    private val prefs = context.getSharedPreferences("hidden", Context.MODE_PRIVATE)

    var albums by mutableStateOf(prefs.getStringSet("albums", null).orEmpty().toSet())
        private set
    var tracks by mutableStateOf(prefs.getStringSet("tracks", null).orEmpty().toSet())
        private set

    fun hide(album: Album) = saveAlbums(albums + album.key)
    fun hide(track: Track) = saveTracks(tracks + track.key)
    fun showAlbum(key: String) = saveAlbums(albums - key)
    fun showTrack(key: String) = saveTracks(tracks - key)

    /** The library without hidden songs, and without albums left empty. */
    fun filter(library: List<Album>): List<Album> {
        if (albums.isEmpty() && tracks.isEmpty()) return library
        return library.mapNotNull { album ->
            if (album.key in albums) return@mapNotNull null
            val visible = album.tracks.filter { it.key !in tracks }
            when {
                visible.isEmpty() -> null
                visible.size == album.tracks.size -> album
                else -> album.copy(tracks = visible)
            }
        }
    }

    private fun saveAlbums(value: Set<String>) {
        albums = value
        prefs.edit().putStringSet("albums", value).apply()
    }

    private fun saveTracks(value: Set<String>) {
        tracks = value
        prefs.edit().putStringSet("tracks", value).apply()
    }
}

private const val SEP = "\u001f"

val Album.key get() = listOf(artist, title).joinToString(SEP)
val Track.key get() = listOf(artist, album, title).joinToString(SEP)

/** A key as readable labels: (title, detail). */
fun hiddenLabel(key: String): Pair<String, String> {
    val parts = key.split(SEP)
    return if (parts.size == 2) parts[1] to parts[0] else parts.last() to "${parts[0]} · ${parts[1]}"
}
