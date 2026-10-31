package com.simonpena.mako

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/** Shuffle that spreads what people notice repeating (docs/radio.md: Spotify's balanced shuffle). */
@RunWith(RobolectricTestRunner::class)
class ShuffleTest {
    // A genre: one artist with 6 songs, three with 2 each.
    private val songs = (1L..6).map { track(it, "Big $it", artist = "Big") } +
        listOf("A", "B", "C").flatMapIndexed { i, a -> (1L..2).map { track(100L * (i + 1) + it, "$a $it", artist = a) } }

    @Test fun `every song once`() {
        val out = spreadShuffle(songs, Random(1)) { it.artist }
        assertEquals(songs.map { it.id }.sorted(), out.map { it.id }.sorted())
    }

    @Test fun `the same artist isn't back to back while others can go between`() {
        repeat(20) { seed ->
            val out = spreadShuffle(songs.filter { it.artist != "Big" } + songs.take(3), Random(seed)) { it.artist }
            assertTrue(out.map { it.artist }.toString(), out.zipWithNext().none { (a, b) -> a.artist == b.artist })
        }
    }

    @Test fun `an artist with many songs is spread across the whole list, not bunched`() {
        val out = spreadShuffle(songs, Random(3)) { it.artist }
        val big = out.indices.filter { out[it].artist == "Big" }
        assertTrue(big.toString(), big.first() <= 2 && big.last() >= out.size - 3)
    }

    @Test fun `seeded, so a test can repeat it, and not the same order every time`() {
        assertEquals(spreadShuffle(songs, Random(5)) { it.artist }, spreadShuffle(songs, Random(5)) { it.artist })
        assertNotEquals(spreadShuffle(songs, Random(5)) { it.artist }, spreadShuffle(songs, Random(6)) { it.artist })
    }
}
