package com.simonpena.mako

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/** The UI's handle on [PlaybackService], exposing player state as Compose state. */
class PlayerHolder(context: Context) {
    private var controller: MediaController? = null

    var isPlaying by mutableStateOf(false)
        private set
    var currentTrackId by mutableStateOf<Long?>(null)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = future.get()
            c.addListener(listener)
            controller = c
            sync(c)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun sync(p: Player) {
        isPlaying = p.isPlaying
        currentTrackId = p.currentMediaItem?.mediaId?.toLongOrNull()
        shuffle = p.shuffleModeEnabled
        repeatMode = p.repeatMode
    }

    /** Plays [tracks] starting at [index]: the queue becomes the album. */
    fun play(tracks: List<Track>, index: Int) {
        controller?.run {
            setMediaItems(tracks.map { it.toMediaItem() }, index, 0L)
            prepare()
            play()
        }
    }

    fun togglePlay() {
        controller?.run {
            if (isPlaying) pause() else {
                if (playbackState == Player.STATE_ENDED) seekToDefaultPosition()
                play()
            }
        }
    }

    fun next() = controller?.seekToNext()
    fun previous() = controller?.seekToPrevious()
    fun seekTo(ms: Long) = controller?.seekTo(ms)
    fun positionMs(): Long = controller?.currentPosition ?: 0L

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    /** Off → all → one → off. */
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun release() {
        controller?.release()
        controller = null
    }
}

private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setDurationMs(durationMs)
            .setArtworkUri(legacyArtworkUri(albumId))
            .build()
    )
    .build()
