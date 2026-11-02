package com.simonpena.makofi

import org.junit.Assert.assertEquals
import org.junit.Test

class StationMetricsTest {
    @Test fun `metrics count picks linked only by genre and era, and station variety`() {
        val station = listOf(
            scored("a", "one", setOf(Reason.Genre("Rock"), Reason.Decade(1990))),
            scored("a", "two", setOf(Reason.Genre("Rock"))),
            scored("b", "three", setOf(Reason.Shares("Producer")), widened = true),
            scored("b", "four", setOf(Reason.Decade(2000)))
        )
        val metrics = StationMetrics.from(station)
        assertEquals(0.75, metrics.onlyGenreAndEra, 0.0001)
        assertEquals(2, metrics.albums)
        assertEquals(2, metrics.artists)
        assertEquals(2, metrics.mostFromOneAlbum)
        assertEquals(1, metrics.widened)
    }

    private fun scored(artist: String, album: String, reasons: Set<Reason>, widened: Boolean = false) = StationPick(
        artist, album, if (album in setOf("one", "two")) 1L else 2L, album, 1990, reasons, widened
    )
}
