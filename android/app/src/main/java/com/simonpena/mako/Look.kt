package com.simonpena.mako

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether Genres and Artists show as tiles or as lists (⋮ menu). Both are
 * kept while the beta decides between them; tiles to start with.
 */
class Look(context: Context) {
    private val prefs = context.getSharedPreferences("look", Context.MODE_PRIVATE)

    private var state by mutableStateOf(prefs.getBoolean("tiles", true))

    var tiles: Boolean
        get() = state
        set(value) {
            state = value
            prefs.edit().putBoolean("tiles", value).apply()
        }
}
