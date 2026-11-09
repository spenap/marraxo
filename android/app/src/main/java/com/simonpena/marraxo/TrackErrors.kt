package com.simonpena.marraxo

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/**
 * Skips a track that can't be played (a corrupt or missing file, a format
 * the speaker refuses) and says which, through [cantPlay]. After
 * [MAX_IN_A_ROW] in a row, or at the end of the queue, playback stops
 * there instead. A lost speaker or TV is not a track's error.
 */
class TrackErrors(private val cantPlay: (title: String) -> Unit) : Player.Listener {
    private var player: Player? = null
    private var inARow = 0

    fun follow(player: Player) {
        this.player?.removeListener(this)
        this.player = player
        player.addListener(this)
        inARow = 0
    }

    override fun onPlayerError(error: PlaybackException) {
        if (error.errorCode == PlaybackException.ERROR_CODE_REMOTE_ERROR) return
        val player = player ?: return
        cantPlay(player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty())
        inARow++
        if (inARow < MAX_IN_A_ROW && player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        } else {
            inARow = 0
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) inARow = 0
    }

    companion object {
        const val MAX_IN_A_ROW = 3
    }
}
