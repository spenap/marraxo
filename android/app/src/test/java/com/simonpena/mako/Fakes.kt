package com.simonpena.mako

import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.robolectric.Shadows.shadowOf
import java.time.Duration

fun track(id: Long, title: String, album: String = "Album", artist: String = "Artist", albumId: Long = 1, durationMs: Long = 180_000) =
    Track(id, title, artist, album, albumId, durationMs, id.toInt(), Uri.parse("content://media/external/audio/media/$id"))

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

    override fun getState(): State {
        val b = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(items.mapIndexed { i, item -> MediaItemData.Builder("${item.mediaId}#$i").setMediaItem(item).build() })
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (items.isNotEmpty() && prepared) Player.STATE_READY else Player.STATE_IDLE)
            .setContentPositionMs(positionMs)
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

    override fun handlePrepare(): ListenableFuture<*> { prepared = true; return done }
    override fun handleStop(): ListenableFuture<*> { prepared = false; return done }
    override fun handleRelease(): ListenableFuture<*> = done

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        return done
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        index = mediaItemIndex
        this.positionMs = if (positionMs == C.TIME_UNSET) 0 else positionMs
        return done
    }

    private val done = Futures.immediateVoidFuture()
}
