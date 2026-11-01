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
    /** The queue in play order, and where playback is in it. */
    var queue by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(0)
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
        if (p.mediaItemCount != queue.size || (0 until p.mediaItemCount).any { p.getMediaItemAt(it) != queue[it] }) {
            queue = List(p.mediaItemCount) { p.getMediaItemAt(it) }
        }
        currentIndex = p.currentMediaItemIndex
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

    /** Plays [seed] now and a radio from it after: the queue becomes the radio. */
    fun startRadio(seed: Track, label: String) {
        controller?.run {
            shuffleModeEnabled = false
            repeatMode = Player.REPEAT_MODE_OFF
            setMediaItems(listOf(seed.toMediaItem(radio = label)), 0, 0L)
            prepare()
            play()
            sendCustomCommand(
                SessionCommand(PlaybackService.START_RADIO, Bundle.EMPTY),
                Bundle().apply {
                    putLong(PlaybackService.EXTRA_SEED, seed.id)
                },
            )
        }
    }

    /** Puts [tracks] right after the current song; during a radio they join the radio. */
    fun playNext(tracks: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(tracks, 0)
        val radio = c.currentMediaItem?.mediaMetadata?.extras?.getString(EXTRA_RADIO)
        c.addMediaItems(c.currentMediaItemIndex + 1, tracks.map { it.toMediaItem(radio = radio) })
    }

    fun playNext(track: Track) = playNext(listOf(track))

    fun togglePlay() {
        controller?.run {
            if (isPlaying) pause() else {
                if (playbackState == Player.STATE_ENDED) seekToDefaultPosition()
                play()
            }
        }
    }

    /** Plays the queue's song at [index]. */
    fun jumpTo(index: Int) {
        controller?.run {
            seekToDefaultPosition(index)
            play()
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
/** The radio a song belongs to, by its mode's name ("Same era"); absent outside a radio. */
const val EXTRA_RADIO = "radio"
/** Why the radio picked a song ("Hugh Padgham on both"). */
const val EXTRA_REASON = "reason"

internal fun Track.toMediaItem(radio: String? = null, reason: String? = null): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setDurationMs(durationMs)
            .setExtras(Bundle().apply {
                putLong(EXTRA_ALBUM_ID, albumId)
                radio?.let { putString(EXTRA_RADIO, it) }
                reason?.let { putString(EXTRA_REASON, it) }
            })
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
