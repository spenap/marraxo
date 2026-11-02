package com.simonpena.makofi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Years tab's scroll label and scrubber (decades when fast, years when slow). */
class YearsTest {
    // The Years list: a heading row per year, then a row per album. 0 = unknown year, last.
    private val rows = YearRows(listOf(2010 to 2, 2002 to 1, 1994 to 3, 1977 to 1, 0 to 1))

    @Test fun `every row knows its year`() {
        assertEquals(13, rows.size) // 5 headings + 8 albums
        assertEquals(2010, rows.yearAt(0)) // heading
        assertEquals(2010, rows.yearAt(2)) // second 2010 album
        assertEquals(2002, rows.yearAt(3))
        assertEquals(1994, rows.yearAt(8))
        assertEquals(0, rows.yearAt(12))
    }

    @Test fun `fast scrolling zooms out, slowing down zooms in, without flicker in between`() {
        val zoom = ScrollZoom(fast = 20f, slow = 8f)
        assertFalse(zoom.coarse)
        zoom.onSpeed(12f); assertFalse("between thresholds stays fine", zoom.coarse)
        zoom.onSpeed(30f); assertTrue(zoom.coarse)
        zoom.onSpeed(12f); assertTrue("between thresholds stays coarse", zoom.coarse)
        zoom.onSpeed(5f); assertFalse(zoom.coarse)
        zoom.onSpeed(-30f); assertTrue("scrolling up counts too", zoom.coarse)
    }

    @Test fun `decade marks sit where each decade starts, spaced by album count`() {
        val marks = rows.decades
        assertEquals(listOf("’10", "’00", "’90", "’70", "?"), marks.map { it.label })
        assertEquals(listOf(0, 3, 5, 9, 11), marks.map { it.row })
        assertEquals(0f, marks.first().fraction, 0.001f)
        assertEquals(11f / 13, marks.last().fraction, 0.001f)
    }

    @Test fun `dragging the scrubber maps its position to a row and back`() {
        assertEquals(0, rows.rowAt(0f))
        assertEquals(12, rows.rowAt(1f))
        assertEquals(12, rows.rowAt(1.5f)) // dragged past the end
        assertEquals(0, rows.rowAt(-0.2f))
        assertEquals(6, rows.rowAt(0.5f))
        assertEquals(6, rows.rowAt(rows.fractionOf(6))) // round trip
    }

    @Test fun `when marks collide, the decade with more albums keeps its mark`() {
        // One album in the 2010s, many in the 2000s: their marks are close together.
        val few = YearRows(listOf(2010 to 1) + (2009 downTo 2000).map { it to 3 } + (1999 downTo 1990).map { it to 3 })
        val labels = few.visibleMarks(heightPx = 600f, minGapPx = 40f).map { it.label }
        assertEquals(listOf("’00", "’90"), labels) // ’10 is dropped, not ’00
    }

    @Test fun `marks that fit are all shown, in order`() {
        assertEquals(listOf("’10", "’00", "’90", "’70", "?"), rows.visibleMarks(heightPx = 2_000f, minGapPx = 40f).map { it.label })
    }
}
