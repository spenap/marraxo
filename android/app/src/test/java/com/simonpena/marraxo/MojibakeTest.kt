package com.simonpena.marraxo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun `a split-off song with a guest rejoins its album`() {
        // You’ll Be Alright, Kid on the S25: only "On My Mind" (Alex Warren & ROSÉ) got its own id.
        val album = "You’ll Be Alright, Kid"
        val tracks = (1..20).map { track(it.toLong(), "Song $it", album = album, artist = "Alex Warren", albumArtist = "Alex Warren", albumId = 7) } +
            track(21, "On My Mind", album = album, artist = "Alex Warren & ROSÉ", albumArtist = "Alex Warren", albumId = 2)
        assertEquals(21, groupAlbums(tracks, years = emptyMap()).single().tracks.size)
    }

    @Test fun `a double album plays disc 1 before disc 2`() {
        val tracks = listOf(
            track(1, "Burning Down", number = 1, disc = 2), track(2, "Eternity", number = 1, disc = 1),
            track(3, "Catch My Breath", number = 2, disc = 2), track(4, "The Outside", number = 2, disc = 1),
        )
        val album = groupAlbums(tracks, years = emptyMap()).single()
        assertEquals(listOf("Eternity", "The Outside", "Burning Down", "Catch My Breath"), album.tracks.map { it.title })
    }

    @Test fun `albums with the same title by different artists stay apart`() {
        val albums = groupAlbums(listOf(
            track(1, "a", album = "Greatest Hits", artist = "Clannad", albumId = 1),
            track(2, "b", album = "Greatest Hits", artist = "Journey", albumId = 2),
        ), years = emptyMap())
        assertEquals(2, albums.size)
    }

    @Test fun `a compilation is by various artists, in the phone's language`() {
        // Anthems: Trance: 60 artists once each, and three DJ mixes credited to MusicBrainz's "[unknown]".
        val tracks = (1..60).map { track(it.toLong(), "Song $it", album = "Anthems: Trance", artist = "Artist $it", albumArtist = "Various Artists") } +
            (61..63).map { track(it.toLong(), "Mix $it", album = "Anthems: Trance", artist = "[desconocido]", albumArtist = "Various Artists") }
        val album = groupAlbums(tracks, years = emptyMap(), various = "Varios artistas").single()
        assertEquals("Varios artistas", album.artist)
        assertTrue(album.mixedArtists)
    }

    @Test fun `a band's album with guests is the band's`() {
        // The Best of Battlefield Band: one track credited to "Fiddlers 5", the rest to the band, some with guests.
        val tracks = listOf(
            track(1, "a", album = "The Best of", artist = "Fiddlers 5", albumArtist = "Battlefield Band"),
            track(2, "b", album = "The Best of", artist = "Battlefield Band feat. Alan Reid", albumArtist = "Battlefield Band"),
            track(3, "c", album = "The Best of", artist = "Battlefield Band", albumArtist = "Battlefield Band"),
        )
        val album = groupAlbums(tracks, years = emptyMap()).single()
        assertEquals("Battlefield Band", album.artist)
        assertTrue(album.mixedArtists) // three different credits: each song says whose it is
    }

    @Test fun `without an album artist the most frequent artist names the album`() {
        val tracks = listOf(track(1, "a", artist = "Altan"), track(2, "b", artist = "Altan"), track(3, "c", artist = "Altan feat. Dolores Keane"))
        assertEquals("Altan", groupAlbums(tracks, years = emptyMap()).single().artist)
    }

    @Test fun `an album by one artist doesn't repeat it on every song`() {
        val tracks = listOf(track(1, "a", artist = "Fun", albumArtist = "Fun"), track(2, "b", artist = "Fun", albumArtist = "Fun"))
        assertFalse(groupAlbums(tracks, years = emptyMap()).single().mixedArtists)
    }
}
