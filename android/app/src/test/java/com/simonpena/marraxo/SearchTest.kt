package com.simonpena.marraxo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchTest {
    private val library = listOf(
        album(1, "Castillo de arena", "Camarón de la Isla", "Castillo de arena", "Arte y majestad"),
        album(2, "A son de guerra", "Juan Luis Guerra", "Apaga y vámonos", "Bachata en Fukuoka"),
        album(3, "The Blue Idol", "Altan", "Daily Growing", "Uncle Rat"),
    )
    private val index = SearchIndex(library)

    @Test fun `accents and case don't matter`() {
        assertEquals(listOf(1L), index.search("CAMARON").first.map { it.id })
        assertEquals("Apaga y vámonos", index.search("vamonos").second.single().let { (a, i) -> a.tracks[i].title })
    }

    @Test fun `artists find their albums`() {
        assertEquals(listOf(2L), index.search("luis").first.map { it.id })
    }

    @Test fun `songs come back with their album and position`() {
        val (album, i) = index.search("uncle").second.single()
        assertEquals(3L, album.id)
        assertEquals(1, i)
    }

    @Test fun `a title matching an album and a song gives both`() {
        val (albums, songs) = index.search("castillo")
        assertEquals(1, albums.size)
        assertEquals(1, songs.size)
    }

    @Test fun `blank queries match nothing`() {
        val (albums, songs) = index.search("   ")
        assertTrue(albums.isEmpty() && songs.isEmpty())
    }

    @Test fun `song results are capped`() {
        val big = SearchIndex(listOf(album(9, "Many", "X", *Array(100) { "Song $it" })))
        assertEquals(10, big.search("song", maxSongs = 10).second.size)
    }
}
