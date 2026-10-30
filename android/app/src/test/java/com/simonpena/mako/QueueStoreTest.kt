package com.simonpena.mako

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val tracks = listOf(
        track(11, "O... Saya", "Slumdog Millionaire", "A.R. Rahman", albumId = 5, durationMs = 215_876),
        track(12, "Riots", "Slumdog Millionaire", "A.R. Rahman", albumId = 5, durationMs = 121_556),
    )

    @Test fun `the queue, track and position come back after a restart`() {
        val before = FakePlayer()
        before.setMediaItems(tracks.map { it.toMediaItem() }, 1, 12_345)
        idle()
        QueueStore(context).run { follow(before); save() }

        val after = FakePlayer()
        QueueStore(context).restore(after)
        idle()

        assertEquals(2, after.mediaItemCount)
        assertEquals(1, after.currentMediaItemIndex)
        assertEquals(12_345, after.currentPosition)
        assertEquals(false, after.playWhenReady) // restored paused
        val item = after.getMediaItemAt(1)
        assertEquals("12", item.mediaId)
        assertEquals(tracks[1].uri, item.localConfiguration?.uri)
        assertEquals("Riots", item.mediaMetadata.title)
        assertEquals(121_556L, item.mediaMetadata.durationMs)
        assertEquals(5L, item.mediaMetadata.extras?.getLong(EXTRA_ALBUM_ID)) // for the cover on the TV
    }

    @Test fun `nothing saved leaves the player empty`() {
        val player = FakePlayer()
        QueueStore(context).restore(player)
        idle()
        assertEquals(0, player.mediaItemCount)
    }
}
