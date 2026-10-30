package com.simonpena.mako

import org.junit.Assert.assertEquals
import org.junit.Test

/** Android sometimes misreads correct UTF-8 tags as Windows-1252 (seen on the Pixel 5, Android 17). */
class MojibakeTest {
    @Test fun `garbled text is repaired`() {
        assertEquals("Whatever People Say I Am, That’s What I’m Not", repairMojibake("Whatever People Say I Am, Thatâ€™s What Iâ€™m Not"))
        assertEquals("O… Saya", repairMojibake("Oâ€¦ Saya"))
        assertEquals("Camarón de la Isla", repairMojibake("CamarÃ³n de la Isla"))
        assertEquals("Caledonia’s Hardy Sons", repairMojibake("Caledoniaâ€™s Hardy Sons"))
    }

    @Test fun `correct text is left alone`() {
        for (s in listOf("Camarón", "That’s", "Dúlamán", "São Paulo", "Mo Choill'", "Plain ASCII", "Ã la carte", "Â")) {
            assertEquals(s, repairMojibake(s))
        }
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class) // Track holds a Uri
class AlbumGroupingTest {
    @Test fun `tracks of one album with two Android album ids become one album`() {
        // Android split the album: 12 tracks under a garbled name (one id), 1 under the right name (another).
        val good = "Whatever People Say I Am, That’s What I’m Not"
        val tracks = (1..12).map { track(it.toLong(), "Song $it", album = good, artist = "Arctic Monkeys", albumId = 799) } +
            track(13, "Perhaps Vampires", album = good, artist = "Arctic Monkeys", albumId = 637)
        val albums = groupAlbums(tracks, years = mapOf(799L to 2005))
        assertEquals(1, albums.size)
        assertEquals(13, albums.single().tracks.size)
        assertEquals(799L, albums.single().id) // the id most tracks share, for the cover
        assertEquals(2005, albums.single().year)
    }

    @Test fun `albums with the same title by different artists stay apart`() {
        val albums = groupAlbums(listOf(
            track(1, "a", album = "Greatest Hits", artist = "Clannad", albumId = 1),
            track(2, "b", album = "Greatest Hits", artist = "Journey", albumId = 2),
        ), years = emptyMap())
        assertEquals(2, albums.size)
    }
}
