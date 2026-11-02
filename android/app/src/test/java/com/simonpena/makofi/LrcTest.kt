package com.simonpena.makofi

import org.junit.Assert.assertEquals
import org.junit.Test

/** Timed lyrics in LRC, as LRCLIB serves them and as people write them by hand. */
class LrcTest {
    @Test fun `lines with their times, in order`() {
        val lrc = "[00:17.12] I feel your breath upon my neck\n[00:21.5] The clock won't stop\n[01:02] And this is what we get"
        assertEquals(
            listOf(LrcLine(17_120, "I feel your breath upon my neck"), LrcLine(21_500, "The clock won't stop"), LrcLine(62_000, "And this is what we get")),
            parseLrc(lrc),
        )
    }

    @Test fun `milliseconds, a repeated chorus, and the order of the song not of the file`() {
        val lrc = "[00:30.250][01:30.250] Chorus\n[00:10.000] Verse"
        assertEquals(listOf(LrcLine(10_000, "Verse"), LrcLine(30_250, "Chorus"), LrcLine(90_250, "Chorus")), parseLrc(lrc))
    }

    @Test fun `tags are not lyrics, but an offset moves every line`() {
        val lrc = "[ar:Phil Collins]\n[ti:In the Air Tonight]\n[offset:+500]\n[00:17.12] Well"
        assertEquals(listOf(LrcLine(16_620, "Well")), parseLrc(lrc)) // positive offset: shown earlier
    }

    @Test fun `an empty line is a pause, kept so the last words don't linger`() {
        assertEquals(listOf(LrcLine(1_000, "Words"), LrcLine(5_000, "")), parseLrc("[00:01.00] Words\n[00:05.00]"))
    }

    @Test fun `word timings are left out, the words kept`() {
        assertEquals(listOf(LrcLine(1_000, "Hello world")), parseLrc("[00:01.00] <00:01.00>Hello <00:01.50>world"))
    }

    @Test fun `which line is sung at a position`() {
        val lines = parseLrc("[00:10.00] One\n[00:20.00] Two\n[00:30.00] Three")
        assertEquals(-1, lineAt(lines, 9_999)) // before the first: none yet
        assertEquals(0, lineAt(lines, 10_000))
        assertEquals(1, lineAt(lines, 29_999))
        assertEquals(2, lineAt(lines, 600_000))
    }

    @Test fun `text that isn't LRC has no timed lines`() {
        assertEquals(emptyList<LrcLine>(), parseLrc("Just words\nwithout times"))
    }

    @Test fun `a card shows the line being sung with one before and two after`() {
        val lines = (0 until 10).map { LrcLine(it * 10_000L, "Line $it") }
        val lyrics = Lyrics(lines, null, false)
        assertEquals(CardLines(listOf("Line 3", "Line 4", "Line 5", "Line 6"), current = 1), cardLines(lyrics, 41_000))
        assertEquals(CardLines(listOf("Line 0", "Line 1", "Line 2", "Line 3"), current = -1), cardLines(lyrics, -1)) // before the first line
        assertEquals(listOf("Line 6", "Line 7", "Line 8", "Line 9"), cardLines(lyrics, 600_000).lines) // the end: the last four
    }

    @Test fun `a card of words without times shows the first lines`() {
        val lyrics = Lyrics(emptyList(), "One\n\nTwo\nThree\nFour\nFive", false)
        assertEquals(CardLines(listOf("One", "Two", "Three", "Four"), current = -1), cardLines(lyrics, 0))
    }

    @Test fun `pauses aren't shown on the card`() {
        val lyrics = Lyrics(listOf(LrcLine(0, "Sing"), LrcLine(5_000, ""), LrcLine(9_000, "Again")), null, false)
        assertEquals(listOf("Sing", "Again"), cardLines(lyrics, 6_000).lines)
    }
}
