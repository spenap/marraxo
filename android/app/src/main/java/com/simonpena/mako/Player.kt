package com.simonpena.mako

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.simonpena.mako.upnp.Renderer
import androidx.media3.session.SessionToken

/** The UI's handle on [PlaybackService], exposing player state as Compose state. */
class PlayerHolder(context: Context) {
    private var controller: MediaController? = null

    var isPlaying by mutableStateOf(false)
        private set
    var currentTrackId by mutableStateOf<Long?>(null)
        private set
    var currentItem by mutableStateOf<MediaItem?>(null)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    /** The speaker or TV playing, or null for this phone. */
    var output by mutableStateOf<Renderer?>(null)
        private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(controller: MediaController, extras: Bundle) = syncOutput(extras)
            })
            .buildAsync()
        future.addListener({
            val c = future.get()
            c.addListener(listener)
            controller = c
            sync(c)
            syncOutput(c.sessionExtras)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun sync(p: Player) {
        isPlaying = p.isPlaying
        currentTrackId = p.currentMediaItem?.mediaId?.toLongOrNull()
        currentItem = p.currentMediaItem
        shuffle = p.shuffleModeEnabled
        repeatMode = p.repeatMode
    }

    private fun syncOutput(extras: Bundle) {
        output = extras.getBundle(PlaybackService.EXTRA_OUTPUT)?.let(Renderer::fromBundle)
    }

    /** Moves playback to [renderer], or back to this phone when null. */
    fun playOn(renderer: Renderer?) {
        controller?.sendCustomCommand(SessionCommand(PlaybackService.SET_OUTPUT, Bundle.EMPTY), renderer?.toBundle() ?: Bundle.EMPTY)
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

const val EXTRA_ALBUM_ID = "albumId"

internal fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setDurationMs(durationMs)
            .setExtras(Bundle().apply { putLong(EXTRA_ALBUM_ID, albumId) })
            .setArtworkUri(legacyArtworkUri(albumId))
            .build()
    )
    .build()

/** The track behind a session item; items from a controller carry their URI in the request metadata. */
internal fun MediaItem.toTrack(): Track = Track(
    id = mediaId.toLongOrNull() ?: 0,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    album = mediaMetadata.albumTitle?.toString().orEmpty(),
    albumId = mediaMetadata.extras?.getLong(EXTRA_ALBUM_ID) ?: 0,
    durationMs = mediaMetadata.durationMs ?: 0,
    number = 0,
    uri = requestMetadata.mediaUri ?: localConfiguration?.uri ?: Uri.EMPTY,
)
