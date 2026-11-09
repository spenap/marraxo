package com.simonpena.marraxo

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Audio files opened from another app ("Open with"). */
@RunWith(RobolectricTestRunner::class)
class OpenFileTest {
    @get:Rule val temp = TemporaryFolder()
    private val tags = Tags("Daily Growing", "Altan", "The Blue Idol", 245_000)

    @Test fun `a MediaStore track plays as itself, so the library and history know it`() {
        val uri = Uri.parse("content://media/external/audio/media/42")
        val item = openedItem(uri, tags)
        assertEquals("42", item.mediaId)
        assertEquals(uri, item.localConfiguration?.uri)
        assertEquals(uri, item.requestMetadata.mediaUri) // the session restores the URI from here
    }

    @Test fun `any other file is known by its URI and plays with its tags`() {
        val uri = Uri.parse("content://com.example.files/root/Download/daily.mp3")
        val item = openedItem(uri, tags)
        assertEquals(uri.toString(), item.mediaId)
        assertEquals(uri, item.requestMetadata.mediaUri)
        val track = item.toTrack()
        assertEquals("Daily Growing", track.title)
        assertEquals("Altan", track.artist)
        assertEquals("The Blue Idol", track.album)
        assertEquals(245_000L, track.durationMs)
        assertNull(item.mediaMetadata.extras) // no album id: no cover to look up
    }

    @Test fun `MediaStore ids come only from MediaStore audio URIs`() {
        assertEquals(42L, mediaStoreId(Uri.parse("content://media/external/audio/media/42")))
        assertEquals(7L, mediaStoreId(Uri.parse("content://media/external_primary/audio/media/7")))
        assertNull(mediaStoreId(Uri.parse("content://media/external/images/media/42")))
        assertNull(mediaStoreId(Uri.parse("content://com.example.files/42")))
        assertNull(mediaStoreId(Uri.parse("file:///sdcard/Music/42")))
    }

    @Test fun `an untagged file is titled by its name`() {
        assertEquals("Voice note 3", titleFromName("Voice note 3.m4a"))
        assertEquals("no extension", titleFromName("no extension"))
    }

    @Test fun `a MediaStore track keeps its album for the cover`() {
        val item = openedItem(Uri.parse("content://media/external/audio/media/42"), tags.copy(albumId = 7))
        assertEquals(7L, item.mediaMetadata.extras?.getLong(EXTRA_ALBUM_ID))
    }

    @Test fun `a file only the sender can read is copied, replacing the previous copy`() {
        val dir = temp.newFolder("opened")
        val first = keepCopy("one".byteInputStream(), dir, "first.mp3")
        val second = keepCopy("two".byteInputStream(), dir, "second.m4a")
        assertEquals("two", second.readText())
        assertEquals("second.m4a", second.name)
        assertFalse(first.exists())
        assertEquals(listOf(second), dir.listFiles()!!.toList())
    }

    @Test fun `a copy's name can't leave its folder`() {
        val dir = temp.newFolder("opened")
        val copy = keepCopy("x".byteInputStream(), dir, "../../evil.mp3")
        assertEquals(dir, copy.parentFile)
    }
}
