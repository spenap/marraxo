package com.simonpena.makofi.upnp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Range requests as Sonos and the TV make them (docs/upnp-renderers.md). */
class ByteRangeTest {
    private val size = 7_067_594L

    @Test fun `no header means the whole file`() {
        assertNull(byteRange(null, size))
        assertNull(byteRange("", size))
        assertNull(byteRange("items=0-10", size))
    }

    @Test fun `open-ended ranges run to the end`() {
        assertEquals(20_695L..size - 1, byteRange("bytes=20695-", size)) // Sonos skipping the ID3 tag
        assertEquals(0L..size - 1, byteRange("bytes=0-", size)) // the TV's first GET
    }

    @Test fun `closed ranges are clamped to the file`() {
        assertEquals(100L..199L, byteRange("bytes=100-199", size))
        assertEquals(size - 10..size - 1, byteRange("bytes=${size - 10}-${size + 500}", size))
    }

    @Test fun `suffix ranges ask for the last bytes`() {
        assertEquals(size - 128..size - 1, byteRange("bytes=-128", size)) // the ID3v1 tag
    }

    @Test fun `unsatisfiable ranges are empty`() {
        assertTrue(byteRange("bytes=${size}-", size)!!.isEmpty())
        assertTrue(byteRange("bytes=200-100", size)!!.isEmpty())
    }
}
