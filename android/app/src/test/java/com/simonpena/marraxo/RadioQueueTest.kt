package com.simonpena.marraxo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlin.random.Random

/** A radio in the player's queue: three songs ahead, until something else is played. */
@RunWith(RobolectricTestRunner::class)
class RadioQueueTest {
    private val library = (1L..12).map { album(it, "LP $it", "Artist $it", "A", "B", "C", genre = "Rock").copy(year = 1984) }
    private val seed = library[0].tracks[0]
    private val radio = Radio(library, RadioWeights.Era, seed, random = Random(1))
    // Picks on the main looper here, so idle() runs them.
    private val reasons = RadioQueue(MainScope(), Dispatchers.Main) { reason -> "because $reason" }

    /** The UI's part: the seed plays at once, marked as a radio. */
    private fun FakePlayer.playSeed() {
        setMediaItems(listOf(seed.toMediaItem(radio = "Same era")))
        prepare()
        play()
        idle()
    }

    private fun FakePlayer.reasonAt(i: Int) = getMediaItemAt(i).mediaMetadata.extras?.getString(EXTRA_REASON)

    @Test fun `a radio keeps three songs ahead, each with why it was picked`() {
        val player = FakePlayer().apply { playSeed() }
        reasons.follow(player)
        reasons.start(radio, seed)
        idle()
        assertEquals(4, player.mediaItemCount)
        assertEquals("because Decade(decade=1980)", player.reasonAt(1))
        assertEquals("Same era", player.getMediaItemAt(3).mediaMetadata.extras?.getString(EXTRA_RADIO))

        player.finishTrack()
        idle()
        assertEquals(5, player.mediaItemCount)
        assertEquals(4, (0 until 5).map { player.getMediaItemAt(it).mediaId }.drop(1).distinct().size)
    }

    @Test fun `playing an album ends the radio`() {
        val player = FakePlayer().apply { playSeed() }
        reasons.follow(player)
        reasons.start(radio, seed)
        idle()
        player.setMediaItems(library[5].tracks.map { it.toMediaItem() })
        idle()
        player.finishTrack()
        idle()
        assertEquals(3, player.mediaItemCount)
        assertNull(player.reasonAt(1))
    }

    @Test fun `a radio that finished building after an album was chosen doesn't take over`() {
        val player = FakePlayer().apply { playSeed() }
        reasons.follow(player)
        player.setMediaItems(library[5].tracks.map { it.toMediaItem() })
        idle()
        reasons.start(radio, seed)
        idle()
        assertEquals(3, player.mediaItemCount)
    }

    @Test fun `the radio goes on when playback moves to a speaker`() {
        val phone = FakePlayer().apply { playSeed() }
        reasons.follow(phone)
        reasons.start(radio, seed)
        idle()
        val speaker = FakePlayer()
        handOver(phone, speaker)
        reasons.follow(speaker)
        idle()
        speaker.finishTrack()
        idle()
        assertEquals(5, speaker.mediaItemCount)
    }
}
