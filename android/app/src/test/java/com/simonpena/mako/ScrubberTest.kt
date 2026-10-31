package com.simonpena.mako

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Letter scrubbers on the Albums and Artists tabs, and when a list gets one. */
class ScrubberTest {
    @Test fun `an artist's letter comes from the same key as the sort`() {
        assertEquals("A", initial("ABBA"))
        assertEquals("C", initial("The Corrs")) // sorted under C, so lettered C
        assertEquals("A", initial("Álvaro Ruiz"))
        assertEquals("N", initial("Ñu"))
        assertEquals("#", initial("2 Unlimited"))
        assertEquals("#", initial("¡Forward, Russia!"))
        assertEquals("#", initial(""))
    }

    @Test fun `accented names sort with their letter, not after Z`() {
        val sorted = listOf("Zucchero", "Amaral", "Álvaro Ruiz", "Altan", "The Beatles").sortedBy(::sortKey)
        assertEquals(listOf("Altan", "Álvaro Ruiz", "Amaral", "The Beatles", "Zucchero"), sorted)
    }

    @Test fun `a mark where each letter starts`() {
        val scrub = letterMarks(listOf("#", "A", "A", "B", "D", "D"))
        assertEquals(6, scrub.size)
        assertEquals(listOf("#", "A", "B", "D"), scrub.marks.map { it.label })
        assertEquals(listOf(0, 1, 3, 4), scrub.marks.map { it.row })
        assertEquals(3f / 6, scrub.marks[2].fraction, 0.001f)
    }

    @Test fun `a letter that comes back later keeps its first mark`() {
        assertEquals(listOf("A", "B"), letterMarks(listOf("A", "B", "A")).marks.map { it.label })
    }

    @Test fun `while dragging, the label is the letter of the row reached`() {
        val scrub = letterMarks(listOf("A", "A", "B", "D", "D"))
        assertEquals("A", scrub.labelAt(1))
        assertEquals("B", scrub.labelAt(2))
        assertEquals("D", scrub.labelAt(4))
    }

    @Test fun `the scrubber maps its position to a row and back`() {
        val scrub = letterMarks(List(10) { "A" })
        assertEquals(0, scrub.rowAt(0f))
        assertEquals(9, scrub.rowAt(1f))
        assertEquals(5, scrub.rowAt(scrub.fractionOf(5)))
    }

    @Test fun `a list gets a scrubber when it is more than three screens long`() {
        assertTrue(needsScrubber(total = 31, visible = 10))
        assertFalse(needsScrubber(total = 30, visible = 10))
        assertFalse(needsScrubber(total = 5, visible = 0)) // not laid out yet
    }
}
