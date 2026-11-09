package com.simonpena.marraxo

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Songs marked with the heart in Now Playing. Kept in the app, not in the
 * files' tags (the collection is the laptop's); a Home of favourites comes
 * later (ROADMAP A3).
 */
class Favourites(context: Context) {
    private val prefs = context.getSharedPreferences("favourites", Context.MODE_PRIVATE)

    var tracks by mutableStateOf(prefs.getStringSet("tracks", null).orEmpty().toSet())
        private set

    fun has(track: Track) = track.key in tracks

    fun toggle(track: Track) {
        tracks = if (has(track)) tracks - track.key else tracks + track.key
        prefs.edit().putStringSet("tracks", tracks).apply()
    }
}
