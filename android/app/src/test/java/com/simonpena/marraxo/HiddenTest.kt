package com.simonpena.marraxo

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HiddenTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val idol = album(1, "The Blue Idol", "Altan", "Daily Growing", "Uncle Rat")
    private val ground = album(2, "Local Ground", "Altan", "Dúlamán")
    private val library = listOf(idol, ground)

    @Test fun `a hidden album disappears`() {
        val hidden = Hidden(context)
        hidden.hide(idol)
        assertEquals(listOf(2L), hidden.filter(library).map { it.id })
    }

    @Test fun `a hidden song leaves the rest of its album`() {
        val hidden = Hidden(context)
        hidden.hide(idol.tracks[1])
        assertEquals(listOf("Daily Growing"), hidden.filter(library).first().tracks.map { it.title })
    }

    @Test fun `an album with every song hidden disappears`() {
        val hidden = Hidden(context)
        hidden.hide(ground.tracks.single())
        assertEquals(listOf(1L), hidden.filter(library).map { it.id })
    }

    @Test fun `showing again restores it`() {
        val hidden = Hidden(context)
        hidden.hide(idol)
        hidden.showAlbum(idol.key)
        assertEquals(2, hidden.filter(library).size)
    }

    @Test fun `hiding survives a restart and a rescan with new ids`() {
        Hidden(context).hide(idol)
        val rescanned = listOf(idol.copy(id = 77, tracks = idol.tracks.map { it.copy(id = it.id + 1000, albumId = 77) }), ground)
        assertEquals(listOf(2L), Hidden(context).filter(rescanned).map { it.id })
    }
}
