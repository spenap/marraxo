package com.simonpena.makofi

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps a radio going in the player's queue: three songs ahead, added as
 * each song starts. The radio ends by itself when what's playing isn't one
 * of its songs, as when an album is played instead, so starting anything
 * else needs no extra step. [describe] words a [Reason] for Now Playing.
 *
 * Picking takes ~60 ms a song on the Nexus 4, so it runs on [worker], not
 * the main thread; each radio has its own [Session], so a new radio never
 * shares a list with one still picking.
 */
class RadioQueue(
    private val scope: CoroutineScope,
    private val worker: CoroutineDispatcher = Dispatchers.Default,
    private val describe: (Reason) -> String,
) : Player.Listener {
    private class Session(val radio: Radio, val label: String?, seed: Track) {
        /** The seed and every song queued, so none comes twice; touched only while picking. */
        val played = mutableListOf(seed)
    }

    private var player: Player? = null
    private var session: Session? = null
    private var picking: Job? = null

    /** Tops up whichever player is current: the phone, a speaker or the TV. */
    fun follow(player: Player) {
        this.player?.removeListener(this)
        this.player = player
        player.addListener(this)
    }

    /** Continues the radio [seed] started, if [seed] is still what's playing. */
    fun start(radio: Radio, seed: Track) {
        val current = player?.currentMediaItem ?: return
        if (current.mediaId != seed.id.toString() || current.radio == null) return
        session = Session(radio, current.radio, seed)
        picking?.cancel()
        picking = null
        topUp()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED)) topUp()
    }

    private fun topUp() {
        val p = player ?: return
        val s = session ?: return
        if (p.currentMediaItem?.radio == null) {
            session = null
            return
        }
        val wanted = AHEAD - (p.mediaItemCount - 1 - p.currentMediaItemIndex)
        if (wanted <= 0 || picking?.isActive == true) return
        picking = scope.launch {
            val start = SystemClock.elapsedRealtime()
            val picks = withContext(worker) { List(wanted) { s.radio.next(s.played)?.also { s.played += it.track } }.filterNotNull() }
            Log.i(TAG, "picked ${picks.size} in ${SystemClock.elapsedRealtime() - start} ms: ${picks.joinToString { "${it.track.artist} (${it.reason})" }}")
            if (session !== s) return@launch
            val now = player ?: return@launch
            if (now.currentMediaItem?.radio == null) return@launch
            now.addMediaItems(picks.map { it.track.toMediaItem(radio = s.label, reason = describe(it.reason)) })
        }
    }

    private val MediaItem.radio get() = mediaMetadata.extras?.getString(EXTRA_RADIO)

    companion object {
        const val AHEAD = 3
        private const val TAG = "RadioQueue"
    }
}
