package com.simonpena.marraxo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Waiting for the media scanner during a rescan. A fixed 120 s limit cut
 * the first big sync short (2,660 files on the Nexus 4), so the library
 * reloaded half-indexed; the wait now lasts as long as files keep coming.
 */
class ScanProgressTest {
    private var clock = 0L
    private fun progress(total: Int) = ScanProgress(total, stallMs = 30_000) { clock }

    @Test fun `a long scan that keeps going is waited for to the end`() {
        val p = progress(60)
        repeat(60) {
            clock += 5_000 // 5 minutes in all, far past the old 120 s limit…
            assertFalse(p.stalled)
            p.onScanned() // …but files keep arriving
        }
        assertTrue(p.finished)
        assertEquals(60, p.scanned)
    }

    @Test fun `a scan that stops answering is given up after 30 s of silence`() {
        val p = progress(5)
        p.onScanned()
        clock += 29_000
        assertFalse(p.stalled)
        clock += 2_000
        assertTrue(p.stalled)
        assertFalse(p.finished)
    }

    @Test fun `a finished scan is never stalled`() {
        val p = progress(1)
        p.onScanned()
        clock += 60_000
        assertTrue(p.finished)
        assertFalse(p.stalled)
    }
}
