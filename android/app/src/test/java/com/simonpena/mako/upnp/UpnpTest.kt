package com.simonpena.mako.upnp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpnpTest {
    @Test fun `parses renderer times`() {
        assertEquals(293_000L, parseUpnpTime("0:04:53"))
        assertEquals(2_035L, parseUpnpTime("0:00:02.035")) // the TV reports milliseconds
        assertEquals(3_723_000L, parseUpnpTime("1:02:03"))
    }

    @Test fun `rejects what renderers send when they don't know`() {
        assertNull(parseUpnpTime("NOT_IMPLEMENTED"))
        assertNull(parseUpnpTime(""))
        assertNull(parseUpnpTime(null))
    }

    @Test fun `formats seek targets`() {
        assertEquals("0:01:30", formatUpnpTime(90_500))
        assertEquals("1:00:00", formatUpnpTime(3_600_000))
    }

    @Test fun `escape and unescape round-trip`() {
        val s = """Tom & Jerry's <"best">"""
        assertEquals(s, unescape(escape(s)))
        assertFalse('<' in escape(s))
    }

    @Test fun `DIDL carries what the TV needs to show and seek`() {
        val xml = didl(
            "http://10.0.0.2:4000/t/abc.mp3?a=1&b=2", "audio/mpeg", "Daily Growing", "Altan", "The Blue Idol",
            293_000, "http://10.0.0.2:4000/c/def.jpg",
        )
        assertTrue(DLNA_FEATURES in xml) // without OP=01 the TV refuses to seek
        assertTrue("duration=\"0:04:53.000\"" in xml) // without it the TV shows 0:00:00
        assertTrue("<upnp:albumArtURI>http://10.0.0.2:4000/c/def.jpg</upnp:albumArtURI>" in xml)
        assertTrue("<dc:creator>Altan</dc:creator>" in xml)
        assertTrue("a=1&amp;b=2" in xml) // the URL is escaped inside XML
    }
}
