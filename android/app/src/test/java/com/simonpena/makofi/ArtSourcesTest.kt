package com.simonpena.makofi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.FileNotFoundException

/** Where a cover comes from: Android's thumbnail first, the file's own picture if that fails. */
class ArtSourcesTest {
    @Test fun `a thumbnail that's gone falls back to the next source`() {
        // The Blue Idol on the Nexus 4: Android's album art entry points to a deleted file.
        val tried = mutableListOf<String>()
        val art = firstArt(
            { tried += "thumbnail"; throw FileNotFoundException("albumthumbs/1793356817329") },
            { tried += "embedded"; "cover" },
        )
        assertEquals("cover", art)
        assertEquals(listOf("thumbnail", "embedded"), tried)
    }

    @Test fun `the first source that has art wins, and none means none`() {
        assertEquals("thumb", firstArt({ "thumb" }, { error("not reached") }))
        assertNull(firstArt<String>({ null }, { null }))
    }
}
