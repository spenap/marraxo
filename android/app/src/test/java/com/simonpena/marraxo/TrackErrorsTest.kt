package com.simonpena.marraxo

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A track that can't play is skipped, with its title said; a run of them stops playback. */
@RunWith(RobolectricTestRunner::class)
class TrackErrorsTest {
    private val said = mutableListOf<String>()
    private val player = FakePlayer()
    private val errors = TrackErrors { said += it }.also { it.follow(player) }

    private fun play(failing: Set<Int>) {
        player.failing = failing
        player.setMediaItems((1L..5L).map { track(it, "Song $it").toMediaItem() })
        player.prepare()
        player.play()
        idle()
    }

    @Test fun `a track that can't play is skipped, saying which`() {
        play(failing = setOf(1))
        player.seekToNextMediaItem()
        idle()
        assertEquals(2, player.currentMediaItemIndex)
        assertTrue(player.isPlaying)
        assertEquals(listOf("Song 2"), said)
    }

    @Test fun `three in a row stop playback`() {
        play(failing = setOf(0, 1, 2, 3))
        assertEquals(2, player.currentMediaItemIndex)
        assertFalse(player.isPlaying)
        assertEquals(listOf("Song 1", "Song 2", "Song 3"), said)
    }

    @Test fun `a track that plays starts the count again`() {
        play(failing = setOf(0, 1, 3, 4))
        assertEquals(2, player.currentMediaItemIndex)
        assertTrue(player.isPlaying)
        player.seekToNextMediaItem()
        idle()
        assertEquals(4, player.currentMediaItemIndex) // the last: nothing to skip to
        assertFalse(player.isPlaying)
        assertEquals(listOf("Song 1", "Song 2", "Song 4", "Song 5"), said)
    }

    @Test fun `a lost speaker is not a track error`() {
        errors.onPlayerError(PlaybackException("gone", null, PlaybackException.ERROR_CODE_REMOTE_ERROR))
        assertEquals(emptyList<String>(), said)
    }
}
