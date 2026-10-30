package com.simonpena.mako

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Moving playback between this phone and a speaker keeps everything but the place. */
@RunWith(RobolectricTestRunner::class)
class HandOverTest {
    @Test fun `queue, position, play state, shuffle and repeat all move across`() {
        val phone = FakePlayer()
        phone.setMediaItems((1L..4L).map { track(it, "Song $it").toMediaItem() }, 2, 61_000)
        phone.repeatMode = Player.REPEAT_MODE_ALL
        phone.shuffleModeEnabled = true
        phone.prepare()
        phone.play()
        idle()

        val speaker = FakePlayer()
        handOver(phone, speaker)
        idle()

        assertEquals(4, speaker.mediaItemCount)
        assertEquals(2, speaker.currentMediaItemIndex)
        assertEquals(61_000, speaker.currentPosition)
        assertTrue(speaker.playWhenReady)
        assertEquals(Player.REPEAT_MODE_ALL, speaker.repeatMode)
        assertTrue(speaker.shuffleModeEnabled)
        assertEquals(Player.STATE_IDLE, phone.playbackState) // the phone stops
    }

    @Test fun `an empty queue moves as nothing, without preparing`() {
        val speaker = FakePlayer()
        handOver(FakePlayer(), speaker)
        idle()
        assertEquals(0, speaker.mediaItemCount)
        assertEquals(Player.STATE_IDLE, speaker.playbackState)
    }
}
