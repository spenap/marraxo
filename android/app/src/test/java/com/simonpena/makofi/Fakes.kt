package com.simonpena.makofi

import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.robolectric.Shadows.shadowOf
import java.time.Duration

fun track(id: Long, title: String, album: String = "Album", artist: String = "Artist", albumId: Long = 1, durationMs: Long = 180_000,
          albumArtist: String = "", number: Int = id.toInt(), disc: Int = 0) =
    Track(id, title, artist, album, albumId, durationMs, number, Uri.parse("content://media/external/audio/media/$id"), albumArtist = albumArtist, disc = disc)

fun album(id: Long, title: String, artist: String, vararg songs: String, genre: String = "") =
    Album(id, title, artist, 0, songs.mapIndexed { i, s -> track(id * 100 + i, s, title, artist, id) }, genre)

/** Runs what is queued on the main looper, optionally letting [ms] of (fake) time pass. */
fun idle(ms: Long = 0) {
    val looper = shadowOf(Looper.getMainLooper())
    if (ms > 0) looper.idleFor(Duration.ofMillis(ms)) else looper.idle()
}

/**
 * A player that does nothing but hold state, so tests can drive what
 * listeners see: play, pause, seek, and a track ending by itself.
 */
class FakePlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    private var items: List<MediaItem> = emptyList()
    private var index = 0
    private var playWhenReady = false
    private var prepared = false
    private var positionMs = 0L
    private var discontinuity: Int? = null
    private var repeat = Player.REPEAT_MODE_OFF
    private var shuffle = false
    private var error: PlaybackException? = null
    /** Positions in the queue whose tracks fail as they load, like a corrupt or missing file. */
    var failing: Set<Int> = emptySet()

    override fun getState(): State {
        val b = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(items.mapIndexed { i, item -> MediaItemData.Builder("${item.mediaId}#$i").setMediaItem(item).build() })
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (items.isNotEmpty() && prepared && error == null) Player.STATE_READY else Player.STATE_IDLE)
            .setPlayerError(error)
            .setContentPositionMs(positionMs)
            .setRepeatMode(repeat)
            .setShuffleModeEnabled(shuffle)
        if (items.isNotEmpty()) b.setCurrentMediaItemIndex(index)
        discontinuity?.let { b.setPositionDiscontinuity(it, 0) }
        discontinuity = null
        return b.build()
    }

    /** The current track played to its end and the next one starts. */
    fun finishTrack() {
        index++
        positionMs = 0
        discontinuity = Player.DISCONTINUITY_REASON_AUTO_TRANSITION
        invalidateState()
    }

    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        items = mediaItems.toList()
        index = if (startIndex == C.INDEX_UNSET) 0 else startIndex
        positionMs = if (startPositionMs == C.TIME_UNSET) 0 else startPositionMs
        return done
    }

    override fun handleAddMediaItems(index: Int, mediaItems: MutableList<MediaItem>): ListenableFuture<*> {
        items = items.take(index) + mediaItems + items.drop(index)
        return done
    }

    override fun handlePrepare(): ListenableFuture<*> { prepared = true; error = null; load(); return done }

    private fun load() {
        if (!prepared || index !in failing) return
        error = PlaybackException("can't decode", null, PlaybackException.ERROR_CODE_DECODING_FAILED)
        prepared = false // idle after an error, until prepared again
    }
    override fun handleStop(): ListenableFuture<*> { prepared = false; return done }
    override fun handleRelease(): ListenableFuture<*> = done

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        return done
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        index = mediaItemIndex
        this.positionMs = if (positionMs == C.TIME_UNSET) 0 else positionMs
        load()
        return done
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> { repeat = repeatMode; return done }
    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> { shuffle = shuffleModeEnabled; return done }

    private val done = Futures.immediateVoidFuture()
}
