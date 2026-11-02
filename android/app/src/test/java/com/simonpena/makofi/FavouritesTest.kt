package com.simonpena.makofi

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavouritesTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val song = album(1, "Some Nights", "Fun", "Carry On").tracks.single()

    @Test fun `a song is a favourite once marked, and not after unmarking`() {
        val favourites = Favourites(context)
        assertFalse(favourites.has(song))
        favourites.toggle(song)
        assertTrue(favourites.has(song))
        favourites.toggle(song)
        assertFalse(favourites.has(song))
    }

    @Test fun `favourites are remembered`() {
        Favourites(context).toggle(song)
        assertTrue(Favourites(context).has(song))
    }
}
