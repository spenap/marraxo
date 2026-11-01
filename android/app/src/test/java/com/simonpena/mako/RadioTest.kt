package com.simonpena.mako

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/** The radio's choices (docs/radio.md), on small libraries with a seeded random. */
@RunWith(RobolectricTestRunner::class)
class RadioTest {
    private fun lp(id: Long, artist: String, year: Int, genre: String, songs: Int = 4) =
        album(id, "LP $id", artist, *Array(songs) { "Song $id.$it" }, genre = genre).copy(year = year)

    /** Ten 1980s rock albums by ten artists, ten 1990s ones, ten 1960s jazz ones. */
    private val library =
        (1L..10).map { lp(it, "Eighties $it", 1980 + it.toInt() % 10, "Rock") } +
            (11L..20).map { lp(it, "Nineties $it", 1990 + it.toInt() % 10, "Rock") } +
            (21L..30).map { lp(it, "Sixties $it", 1960 + it.toInt() % 10, "Jazz") }

    private val seed = library[0].tracks[0]

    /** [n] picks, each played in turn. */
    private fun Radio.play(n: Int): List<Pick> {
        val played = mutableListOf(seed)
        return List(n) { next(played)!!.also { played += it.track } }
    }

    private fun decade(p: Pick) = library.first { it.id == p.track.albumId }.year / 10 * 10

    @Test fun `same era keeps to the seed's decade while it has music left`() {
        val picks = Radio(library, RadioWeights.Era, seed, random = Random(1)).play(20)
        assertEquals(List(20) { 1980 }, picks.map(::decade))
        assertTrue(picks.all { it.reason == Reason.Decade(1980) })
    }

    @Test fun `similar keeps to the genre, near the decade, and the reason says which`() {
        val picks = Radio(library, RadioWeights.Similar, seed, random = Random(2)).play(20)
        assertTrue(picks.map(::decade).toString(), picks.none { decade(it) == 1960 })
        assertTrue(picks.map { it.reason }.toString(), picks.all { it.reason == Reason.Genre("Rock") })
    }

    @Test fun `no song twice, no artist again within 5 songs, no album within 3`() {
        val played = mutableListOf(seed)
        val radio = Radio(library, RadioWeights.Similar, seed, random = Random(3))
        repeat(60) { played += radio.next(played)!!.track }
        assertEquals(played.size, played.distinctBy { it.id }.size)
        played.windowed(6).forEach { w -> assertEquals(w.toString(), 6, w.distinctBy { it.artist }.size) }
    }

    @Test fun `an album's songs still come together when the library is one album`() {
        val one = listOf(lp(1, "Solo", 1984, "Rock", songs = 5))
        val played = mutableListOf(one[0].tracks[0])
        val radio = Radio(one, RadioWeights.Similar, played[0], random = Random(4))
        repeat(4) { played += radio.next(played)!!.track }
        assertEquals(5, played.distinctBy { it.id }.size)
        assertNull("every song has played", radio.next(played))
    }

    @Test fun `when the close music runs out it widens instead of stopping, and says so`() {
        val small = library.take(2) + library.drop(20) // two 1980s albums, then jazz
        val picks = Radio(small, RadioWeights.Era, seed, random = Random(5)).play(20)
        assertEquals(7, picks.count { decade(it) == 1980 }) // the rest of the seed's album and the other 1980s one
        assertEquals(Reason.Wider, picks.first { decade(it) != 1980 }.reason)
    }

    @Test fun `same people follows the liner notes from album to album`() {
        // LP 1's producer also made LP 25 (jazz, 1965); nothing else is linked.
        val links = mapOf(1L to setOf("Hugh Padgham"), 25L to setOf("Hugh Padgham"))
        val first = Radio(library, RadioWeights.People, seed, links, Random(6)).next(listOf(seed))!!
        assertEquals(25L, first.track.albumId)
        assertEquals(Reason.Shares("Hugh Padgham"), first.reason)
    }

    @Test fun `anything goes wanders across the library`() {
        val picks = Radio(library, RadioWeights.Anything, seed, random = Random(7)).play(20)
        assertTrue(picks.map(::decade).distinct().toString(), picks.map(::decade).distinct().size == 3)
    }

    @Test fun `a mix with one feed at 1 and the rest at 0 is that feed`() {
        assertEquals(RadioWeights.Era, RadioWeights.mix(RadioWeights.Era to 1.0, RadioWeights.People to 0.0))
        assertEquals(RadioWeights.People, RadioWeights.mix(RadioWeights.People to 2.0))
    }

    @Test fun `a mix is the weighted average of its feeds`() {
        val mix = RadioWeights.mix(RadioWeights.Similar to 1.0, RadioWeights.Era to 3.0)
        assertEquals((3.0 + 3 * 1.0) / 4, mix.genre, 1e-9)
        assertEquals((0.6 + 3 * 0.75) / 4, mix.band, 1e-9)
    }

    @Test fun `the same seed and random give the same radio`() {
        val a = Radio(library, RadioWeights.Similar, seed, random = Random(8)).play(10)
        val b = Radio(library, RadioWeights.Similar, seed, random = Random(8)).play(10)
        assertEquals(a, b)
    }
}
