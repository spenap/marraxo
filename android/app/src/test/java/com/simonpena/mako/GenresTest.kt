package com.simonpena.mako

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Genres tab: tags tidied into genres, and genres that share a word into families. */
class GenresTest {
    private var nextId = 0L
    private fun albums(vararg tags: Pair<Int, String>) =
        tags.flatMap { (n, tag) -> List(n) { Album(++nextId, "Album $nextId", "Artist", 0, emptyList(), tag) } }

    /** The Nexus 4's library on 2026-10-31: albums per genre tag. */
    private val library = albums(
        29 to "Celtic", 22 to "Pop", 16 to "Rock", 10 to "Soundtrack", 9 to "Pop español", 6 to "Latin",
        6 to "Indie", 6 to "Heavy Metal", 5 to "Rock & Roll", 5 to "Funky Rock", 5 to "Celtic/Rock", 4 to "Folk",
        3 to "Son Cubano", 3 to "Ambient", 2 to "Pop-Rock", 2 to "Pop espańol", 2 to "Pop Rock", 2 to "Gothic Rock",
        2 to "Country", 1 to "Rock/Classic Rock/70'S", 1 to "Rock en Español", 1 to "Rock Español", 1 to "Reggae",
        1 to "Rap", 1 to "Radio", 1 to "Punk Rock", 1 to "Oldies", 1 to "New Age", 1 to "Indie Rock", 1 to "Indie Pop",
        1 to "Flamenco", 1 to "Classic Rock", 1 to "Choirs", 1 to "Children's Music", 1 to "Celta", 1 to "Britpop",
        1 to "Alternative", 1 to "Acoustic", 4 to "",
    )
    private val shelves = genreShelves(genres(library))
    private fun shelf(name: String) = shelves.first { it.name == name }

    @Test fun `tags that differ in case, accents or punctuation are one genre, named as most albums spell it`() {
        val pop = genres(albums(9 to "Pop español", 2 to "Pop espańol", 2 to "Pop Rock", 2 to "Pop-Rock"))
        assertEquals(listOf("Pop español" to 11, "Pop Rock" to 4), pop.map { it.name to it.albums.size })
    }

    @Test fun `a tag with several genres puts the album in each`() {
        val all = genres(albums(1 to "Rock/Classic Rock/70'S"))
        assertEquals(listOf("70'S", "Classic Rock", "Rock"), all.map { it.name })
        assertTrue(all.all { it.albums.size == 1 })
    }

    @Test fun `albums without a genre are left for the "No genre" row`() {
        assertTrue(genres(library).none { it.name.isEmpty() })
    }

    @Test fun `genres that share a word become a family, and a genre can be in two`() {
        assertEquals(listOf("Español", "Indie", "Pop", "Rock"), shelves.filter { it.isFamily }.map { it.name })
        assertEquals(listOf("Indie", "Indie Pop", "Indie Rock"), shelf("Indie").genres.map { it.name })
        assertTrue("Indie Pop" in shelf("Pop").genres.map { it.name })
        assertEquals(43, shelf("Rock").albums.size) // each album once, though some are in two of its genres
        assertEquals(38, shelf("Pop").albums.size)
    }

    @Test fun `the top level has the families and every genre outside them`() {
        assertEquals(25, shelves.size) // 38 tags before
        assertTrue(shelves.none { it.name == "Pop" && !it.isFamily }) // Pop is inside its family
        assertFalse(shelf("Celtic").isFamily) // Celta is another word: the reviewed tag mapping's job (Track C)
        assertEquals(34, shelf("Celtic").albums.size) // with Celtic/Rock
    }

    @Test fun `short and generic words make no family`() {
        val names = genreShelves(genres(albums(1 to "Children's Music", 1 to "Film Music", 1 to "Rock en Español", 1 to "Pop en Inglés")))
        assertTrue(names.none { it.isFamily })
    }

    @Test fun `words that always come together make one family, not two`() {
        val heavy = genreShelves(genres(albums(1 to "Heavy Metal", 1 to "Heavy Metal Live")))
        assertEquals(listOf("Heavy Metal"), heavy.map { it.name })
        assertEquals(2, heavy.single().genres.size)
    }

    @Test fun `a small family opens straight to its albums, a big one to its genres`() {
        assertTrue(opensAlbums(shelf("Indie"))) // 8 albums
        assertFalse(opensAlbums(shelf("Rock")))
        assertTrue(opensAlbums(shelf("Celtic"))) // a genre always does
    }
}
