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
        assertEquals(listOf("Español", "Indie", "Pop", "Rock"), shelves.filter { it.isFamily && !it.isOther }.map { it.name })
        assertEquals(listOf("Indie", "Indie Pop", "Indie Rock"), shelf("Indie").genres.map { it.name })
        assertTrue("Indie Pop" in shelf("Pop").genres.map { it.name })
        assertEquals(43, shelf("Rock").albums.size) // each album once, though some are in two of its genres
        assertEquals(38, shelf("Pop").albums.size)
    }

    @Test fun `the top level has the families and every genre outside them`() {
        assertEquals(13, shelves.size) // 38 tags before
        assertTrue(shelves.none { it.name == "Pop" && !it.isFamily }) // Pop is inside its family
        assertFalse(shelf("Celtic").isFamily) // Celta is another word: the reviewed tag mapping's job (Track C)
        assertEquals(34, shelf("Celtic").albums.size) // with Celtic/Rock
    }

    @Test fun `short and generic words make no family`() {
        val names = genreShelves(genres(albums(2 to "Children's Music", 2 to "Film Music", 2 to "Rock en Español", 2 to "Pop en Inglés")))
        assertTrue(names.none { it.isFamily })
    }

    @Test fun `words that always come together make one family, not two`() {
        val heavy = genreShelves(genres(albums(1 to "Heavy Metal", 1 to "Heavy Metal Live")))
        assertEquals(listOf("Heavy Metal"), heavy.map { it.name })
        assertEquals(2, heavy.single().genres.size)
    }

    @Test fun `genres with a single album are gathered into Other, at the end`() {
        val other = shelves.last()
        assertTrue(other.isOther)
        assertEquals("", other.name) // the screen names it, in the phone's language
        assertEquals(
            listOf("70'S", "Acoustic", "Alternative", "Britpop", "Celta", "Children's Music", "Choirs", "Flamenco",
                "New Age", "Oldies", "Radio", "Rap", "Reggae"),
            other.genres.map { it.name },
        )
        assertTrue(shelves.filter { !it.isFamily }.all { it.albums.size > 1 })
        assertFalse(opensAlbums(other)) // 13 albums: it opens to its genres
        assertTrue("Indie Rock" in shelf("Rock").genres.map { it.name }) // inside a family, a one-album genre stays
    }

    @Test fun `a lone single-album genre stays where it is`() {
        val few = genreShelves(genres(albums(3 to "Folk", 1 to "Flamenco")))
        assertEquals(listOf("Flamenco", "Folk"), few.map { it.name })
        assertTrue(few.none { it.isOther })
    }

    @Test fun `a small family opens straight to its albums, a big one to its genres`() {
        assertTrue(opensAlbums(shelf("Indie"))) // 8 albums
        assertFalse(opensAlbums(shelf("Rock")))
        assertTrue(opensAlbums(shelf("Celtic"))) // a genre always does
    }

    // Tiles: each shows one cover from its genre, a different one on each visit to the tab.
    private val three = GenreShelf("Celtic", genres(albums(3 to "Celtic")))

    @Test fun `each visit to the tab starts one album further on, through all of them`() {
        val firsts = (0 until 3).map { coverCandidates(three, it).first().id }
        assertEquals(3, firsts.toSet().size)
        assertEquals(coverCandidates(three, 0).first(), coverCandidates(three, 3).first()) // and round again
    }

    @Test fun `the candidates are every album once, so a tile finds artwork if any album has it`() {
        assertEquals(three.albums.map { it.id }.sorted(), coverCandidates(three, 7).map { it.id }.sorted())
    }

    @Test fun `the same visit gives the same cover, so going in and out doesn't reshuffle`() {
        assertEquals(coverCandidates(shelf("Rock"), 5), coverCandidates(shelf("Rock"), 5))
    }

    @Test fun `a genre with no albums has no cover to try`() {
        assertTrue(coverCandidates(GenreShelf("Empty", emptyList()), 1).isEmpty())
    }

    @Test fun `a tile shows the first candidate not known to lack artwork, even if another is already loaded`() {
        val (a, b, c) = coverCandidates(three, 0)
        assertEquals(a, coverToShow(listOf(a, b, c)) { false })
        assertEquals(b, coverToShow(listOf(a, b, c)) { it == a.id }) // a has no artwork
        assertEquals(null, coverToShow(listOf(a, b, c)) { true })
    }

    @Test fun `tile covers load at rest and under a finger that stays, not mid-swipe or mid-fling`() {
        assertTrue(coversMayLoad(scrolling = false, heldMs = 0))
        assertTrue(coversMayLoad(scrolling = true, heldMs = HOLD_MS)) // a slow drag: the finger waits on the screen
        assertFalse(coversMayLoad(scrolling = true, heldMs = 120)) // a quick swipe, about to fling
        assertFalse(coversMayLoad(scrolling = true, heldMs = 0)) // flinging: decoding made Artists stutter
    }
}
