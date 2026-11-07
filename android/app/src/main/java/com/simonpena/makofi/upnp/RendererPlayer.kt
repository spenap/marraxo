package com.simonpena.makofi.upnp

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * A Media3 player that plays on a UPnP renderer instead of the phone.
 *
 * The queue stays on the phone; the renderer gets one track at a time from
 * [MediaServer] and is polled for its state: every second near a track's
 * end and after a command, every few seconds otherwise. Media3 treats it
 * like any other player, so the notification, lock screen, volume keys and
 * play history work unchanged.
 *
 * State changes are applied here at once and the SOAP calls follow on a
 * background thread; polling then corrects anything the renderer did
 * differently (or that another controller, like the Sonos app, changed).
 */
class RendererPlayer(
    context: Context,
    val renderer: Renderer,
    private val server: MediaServer,
    private val random: Random = Random.Default,
    private val cover: (MediaItem) -> ByteArray?,
) : SimpleBasePlayer(Looper.getMainLooper()) {
    private val main = Handler(Looper.getMainLooper())
    private val net = Executors.newSingleThreadExecutor()
    private val wifiLock = context.applicationContext.getSystemService(WifiManager::class.java)
        .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "mako:renderer").apply { setReferenceCounted(false) }
    private val wakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mako:renderer").apply { setReferenceCounted(false) }

    private var items: List<MediaItem> = emptyList() // in play order
    private var uids: List<Any> = emptyList()
    private var albumOrder: List<Any>? = null // uids in their original order while shuffled
    private var repeatMode = Player.REPEAT_MODE_OFF
    private var index = 0
    private var playWhenReady = false
    private var playbackState = Player.STATE_IDLE
    private var positionMs = 0L
    private var positionAt = 0L // elapsedRealtime when positionMs was true
    // The furthest point reached in this track: Sonos briefly reports 0:00 as a
    // track ends, so the last position alone can't tell an end from a stop.
    private var furthestMs = 0L
    /** The track already resumed after the speaker stopped it by itself; a second stop is respected. */
    private var resumed: MediaItem? = null
    private var rendererDurationMs: Long? = null // the length the renderer measured, if it says
    private var volume = 0
    private var muted = false
    private var playerError: PlaybackException? = null
    private var discontinuity: Int? = null

    private var loadedUrl: String? = null // what the renderer should be playing
    private var loading = false // a load is on its way; loadedUrl not known yet
    private var loadedAt = 0L
    private var baseUrl: String? = null // only touched on the net thread
    private var joined = false // net thread: group members follow the lead room
    private var polling = false
    private var missedPolls = 0 // in a row, with no answer at all
    private var released = false

    // ------------------------------------------------------------ state

    override fun getState(): State {
        val playing = playWhenReady && playbackState == Player.STATE_READY
        val position = currentPositionMs()
        val builder = State.Builder()
            .setAvailableCommands(COMMANDS)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (items.isEmpty() && playbackState != Player.STATE_ENDED) Player.STATE_IDLE else playbackState)
            .setPlayerError(playerError)
            .setRepeatMode(repeatMode)
            .setShuffleModeEnabled(albumOrder != null)
            .setDeviceInfo(DEVICE_INFO)
            .setDeviceVolume(volume)
            .setIsDeviceMuted(muted)
            .setPlaylist(items.mapIndexed { i, item ->
                MediaItemData.Builder(uids[i])
                    .setMediaItem(item)
                    .setMediaMetadata(item.mediaMetadata)
                    .setDurationUs(item.mediaMetadata.durationMs?.times(1000) ?: C.TIME_UNSET)
                    .setIsSeekable(true)
                    .build()
            })
            .setContentPositionMs(
                if (playing) PositionSupplier.getExtrapolating(position, 1f) else PositionSupplier.getConstant(position)
            )
        if (items.isNotEmpty()) builder.setCurrentMediaItemIndex(index)
        discontinuity?.let { builder.setPositionDiscontinuity(it, 0) }
        discontinuity = null
        return builder.build()
    }

    private fun currentPositionMs(): Long {
        val playing = playWhenReady && playbackState == Player.STATE_READY
        return positionMs + if (playing) SystemClock.elapsedRealtime() - positionAt else 0
    }

    private fun setPosition(ms: Long) {
        positionMs = ms
        positionAt = SystemClock.elapsedRealtime()
    }

    /** A position the renderer reported while playing. */
    private fun reached(ms: Long) {
        setPosition(ms)
        furthestMs = maxOf(furthestMs, ms)
    }

    // ------------------------------------------------------------ commands

    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        items = mediaItems.toList()
        uids = items.map { Any() }
        index = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, maxOf(0, items.size - 1))
        if (albumOrder != null) shuffle()
        setPosition(if (startPositionMs == C.TIME_UNSET) 0 else startPositionMs)
        if (playbackState != Player.STATE_IDLE) load()
        return DONE
    }

    /** A radio adds songs ahead; while shuffled they go at the end of album order too. */
    override fun handleAddMediaItems(index: Int, mediaItems: MutableList<MediaItem>): ListenableFuture<*> {
        val added = mediaItems.map { Any() }
        items = items.take(index) + mediaItems + items.drop(index)
        uids = uids.take(index) + added + uids.drop(index)
        albumOrder = albumOrder?.plus(added)
        if (index <= this.index && items.size > mediaItems.size) this.index += mediaItems.size
        return DONE
    }

    override fun handlePrepare(): ListenableFuture<*> {
        playerError = null
        if (items.isNotEmpty()) load()
        return DONE
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        setPosition(currentPositionMs())
        this.playWhenReady = playWhenReady
        updateLocks()
        when {
            // The renderer was taken over by something else: take it back.
            playWhenReady && loadedUrl == null && !loading && items.isNotEmpty() -> load()
            loadedUrl != null -> call { playOrPause(playWhenReady) }
        }
        return DONE
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val target = if (positionMs == C.TIME_UNSET) 0 else positionMs
        if (mediaItemIndex != index || loadedUrl == null) {
            index = mediaItemIndex
            setPosition(target)
            if (playbackState != Player.STATE_IDLE) load()
        } else {
            setPosition(target)
            furthestMs = target
            loadedAt = SystemClock.elapsedRealtime() // ignore stale positions for a moment
            call { avt("Seek", "Unit" to "REL_TIME", "Target" to formatUpnpTime(target)) }
        }
        return DONE
    }

    override fun handleStop(): ListenableFuture<*> {
        playbackState = Player.STATE_IDLE
        loadedUrl = null
        updateLocks()
        call { avt("Stop") }
        return DONE
    }

    override fun handleRelease(): ListenableFuture<*> {
        released = true
        main.removeCallbacks(poll)
        val wasLoaded = loadedUrl != null
        loadedUrl = null
        playWhenReady = false
        updateLocks()
        if (wasLoaded) call { avt("Stop") }
        if (renderer.isGroup) call { leaveGroup() }
        net.shutdown() // after the queued calls above have run
        return DONE
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        this.repeatMode = repeatMode
        return DONE
    }

    /**
     * Media3 1.5's SimpleBasePlayer walks the playlist in order even with
     * shuffle on, so shuffling reorders the queue itself: the current track
     * first, the rest at random. Next, previous and the notification then
     * work unchanged. Turning it off restores album order.
     */
    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        if (shuffleModeEnabled && albumOrder == null) shuffle()
        if (!shuffleModeEnabled && albumOrder != null) unshuffle()
        return DONE
    }

    private fun shuffle() {
        albumOrder = uids
        if (items.isEmpty()) return
        val order = listOf(index) + (items.indices - index).shuffled(random)
        items = order.map(items::get)
        uids = order.map(uids::get)
        index = 0
    }

    private fun unshuffle() {
        val original = albumOrder ?: return
        albumOrder = null
        val byUid = uids.zip(items).toMap()
        val current = uids.getOrNull(index)
        uids = original
        items = original.map(byUid::getValue)
        index = maxOf(0, original.indexOf(current))
    }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        volume = deviceVolume.coerceIn(0, 100)
        val target = volume
        call { setRendererVolume(target) }
        return DONE
    }

    override fun handleIncreaseDeviceVolume(flags: Int) = handleSetDeviceVolume(volume + VOLUME_STEP, flags)

    override fun handleDecreaseDeviceVolume(flags: Int) = handleSetDeviceVolume(volume - VOLUME_STEP, flags)

    override fun handleSetDeviceMuted(muted: Boolean, flags: Int): ListenableFuture<*> {
        this.muted = muted
        val mute = if (muted) 1 else 0
        call {
            if (renderer.isGroup && renderer.groupRenderingControl != null) grc("SetGroupMute", "DesiredMute" to mute)
            else rc("SetMute", "Channel" to "Master", "DesiredMute" to mute)
        }
        return DONE
    }

    // ------------------------------------------------------------ renderer

    /** Sends the current item to the renderer, starting at [positionMs]. */
    private fun load() {
        val item = items.getOrNull(index) ?: return
        val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri ?: return
        val start = positionMs
        val play = playWhenReady
        furthestMs = start
        rendererDurationMs = null
        playbackState = Player.STATE_BUFFERING
        loadedAt = SystemClock.elapsedRealtime()
        loading = true
        val host = java.net.URL(renderer.avTransport).host
        val meta = item.mediaMetadata
        call(loadsTrack = true) {
            if (renderer.isGroup && !joined) joinGroup()
            val base = baseUrl ?: server.baseUrlFor(host).also { baseUrl = it }
            val (path, mime) = server.publishTrack(item.mediaId, uri)
            val url = base + path
            val art = cover(item)?.let { base + server.publishCover(item.mediaId, it) }
            main.post {
                loading = false
                if (items.getOrNull(index) !== item) return@post
                loadedUrl = url
                // Play or pause asked for while loading; queued after this load's own calls.
                if (playWhenReady != play) call { playOrPause(playWhenReady) }
            }
            val metadata = didl(
                url, mime, meta.title?.toString(), meta.artist?.toString(), meta.albumTitle?.toString(),
                meta.durationMs, art,
            )
            avt("SetAVTransportURI", "CurrentURI" to url, "CurrentURIMetaData" to metadata)
            if (play) avt("Play", "Speed" to 1)
            settleFromNow()
            // The TV only seeks once it is playing.
            if (start > 1000) runCatching { avt("Seek", "Unit" to "REL_TIME", "Target" to formatUpnpTime(start)) }
        }
    }

    /** Asks the renderer for its state; the next poll is scheduled from what it says. */
    private val poll = object : Runnable {
        override fun run() {
            if (released || polling || net.isShutdown) return // a reply on its way schedules the next
            polling = true
            net.execute {
                val transport = runCatching { avt("GetTransportInfo") }.getOrNull()
                val position = runCatching { avt("GetPositionInfo") }.getOrNull()
                val volume = runCatching { rendererVolume() }.getOrNull()
                main.post {
                    polling = false
                    missedPolls = if (transport == null && position == null && volume == null) missedPolls + 1 else 0
                    if (missedPolls >= MAX_MISSED_POLLS) {
                        fail(Unreachable(IOException("no answer to $MAX_MISSED_POLLS polls")), PlaybackException.ERROR_CODE_REMOTE_ERROR)
                        return@post
                    }
                    applyRemote(transport, position, volume)
                    if (!released) {
                        main.removeCallbacks(this)
                        main.postDelayed(this, pollDelayMs())
                    }
                }
            }
        }
    }

    /**
     * Each poll is three requests that wake the phone's Wi-Fi, so only moments
     * that need a quick answer get one: right after a command, while loading,
     * and in the window where a stop means the track ended (next track without
     * a gap). Elsewhere a stop or a change from another controller is noticed
     * a few seconds later.
     */
    private fun pollDelayMs(): Long {
        val busy = loading || playbackState == Player.STATE_BUFFERING ||
            SystemClock.elapsedRealtime() - loadedAt < SETTLE_MS + FAST_POLL_MS
        val nearEnd = playWhenReady && loadedUrl != null && currentPositionMs() >= endWindowStartMs()
        return if (busy || nearEnd) FAST_POLL_MS else SLOW_POLL_MS
    }

    /** After a command: poll soon rather than at the next slow poll. */
    private fun pollSoon() {
        if (released) return
        main.removeCallbacks(poll)
        main.postDelayed(poll, FAST_POLL_MS)
    }

    /**
     * Where a stop counts as the track's end. Renderer positions can't
     * confirm an end precisely: for VBR MP3s Sonos's clock drifts behind the
     * audio (Arregla Los Papeles ended at a reported 3:23 of 3:35). So the
     * last fifth of a track counts as its end.
     */
    private fun endWindowStartMs(): Long {
        val duration = minOf(items.getOrNull(index)?.mediaMetadata?.durationMs ?: Long.MAX_VALUE, rendererDurationMs ?: Long.MAX_VALUE)
        if (duration == Long.MAX_VALUE) return Long.MAX_VALUE
        return duration - maxOf(END_SLACK_MS, duration / 5)
    }

    init {
        main.post(poll)
    }

    private fun applyRemote(transport: Map<String, String>?, position: Map<String, String>?, volume: Int?) {
        if (released) return
        volume?.let { this.volume = it }
        val state = transport?.get("CurrentTransportState")
        val loaded = loadedUrl
        if (state == null || loaded == null || playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
            invalidateState()
            return
        }
        val settling = SystemClock.elapsedRealtime() - loadedAt < SETTLE_MS
        val uri = position?.get("TrackURI").orEmpty()
        if (!settling && uri.isNotEmpty() && uri != loaded) {
            // Another controller (the Sonos app, another phone) took over.
            Log.i(TAG, "${renderer.name} now plays $uri; pausing here")
            setPosition(currentPositionMs())
            playWhenReady = false
            loadedUrl = null
            playbackState = Player.STATE_READY
            updateLocks()
            invalidateState()
            return
        }
        val lastPosition = currentPositionMs()
        when (state) {
            "PLAYING" -> {
                playbackState = Player.STATE_READY
                if (!settling) {
                    parseUpnpTime(position?.get("RelTime"))?.let(::reached)
                    parseUpnpTime(position?.get("TrackDuration"))?.takeIf { it > 0 }?.let { rendererDurationMs = it }
                    if (!playWhenReady) { playWhenReady = true; updateLocks(); Log.i(TAG, "${renderer.name}: resumed elsewhere") }
                }
            }
            "PAUSED_PLAYBACK" -> {
                playbackState = Player.STATE_READY
                if (!settling) {
                    parseUpnpTime(position?.get("RelTime"))?.let(::setPosition)
                    if (playWhenReady) { playWhenReady = false; updateLocks(); Log.i(TAG, "${renderer.name}: paused elsewhere") }
                }
            }
            "TRANSITIONING" -> playbackState = Player.STATE_BUFFERING
            "STOPPED", "NO_MEDIA_PRESENT" -> if (!settling && playWhenReady) {
                // A renderer that stops on its own while still holding our track has
                // almost always reached the end: a takeover changes the track and a
                // pause says PAUSED. Near the end it counts as one (endWindowStartMs);
                // earlier, it's a stop.
                val ended = maxOf(lastPosition, furthestMs) >= endWindowStartMs()
                // One line per decision, so a wrong one can be explained afterwards.
                Log.i(TAG, "${renderer.name}: $state at last=$lastPosition furthest=$furthestMs endsFrom=${endWindowStartMs()} " +
                    "reported=${position?.get("RelTime")} → ${if (ended) "track ended" else "stopped mid-track"}")
                val item = items.getOrNull(index)
                if (ended) advance() else if (item != null && resumed !== item) {
                    // Mid-track, still holding our track: most likely the speaker
                    // giving up (Estudio did, 10 s into a track, after 3 h). Resume
                    // once where it was; a second stop on this track is a person.
                    resumed = item
                    Log.i(TAG, "${renderer.name}: resuming once at ${maxOf(lastPosition, furthestMs)}")
                    setPosition(maxOf(lastPosition, furthestMs))
                    load()
                } else {
                    // Stopped elsewhere, mid-track.
                    playWhenReady = false
                    loadedUrl = null
                    playbackState = Player.STATE_READY
                    updateLocks()
                }
            }
        }
        invalidateState()
    }

    /** The renderer finished the current track: next, again, or the end. */
    private fun advance() {
        Log.i(TAG, "${renderer.name}: advancing from $index of ${items.size}, repeat=$repeatMode")
        val next = when {
            repeatMode == Player.REPEAT_MODE_ONE -> index
            index + 1 < items.size -> index + 1
            repeatMode == Player.REPEAT_MODE_ALL -> 0
            else -> null
        }
        if (next != null) {
            index = next
            setPosition(0)
            discontinuity = Player.DISCONTINUITY_REASON_AUTO_TRANSITION
            load()
        } else {
            playbackState = Player.STATE_ENDED
            loadedUrl = null
            updateLocks()
        }
    }

    /** Net thread: sends Play or Pause, then gives the renderer time to get there. */
    private fun playOrPause(play: Boolean) {
        if (play) avt("Play", "Speed" to 1) else avt("Pause")
        settleFromNow()
    }

    /**
     * Net thread: renderers report stale or in-between states for a moment
     * after a command (the TV can sit in STOPPED, then TRANSITIONING, for
     * seconds), so state that contradicts us is ignored until SETTLE_MS
     * after the command arrived, not after it was queued.
     */
    private fun settleFromNow() {
        main.post {
            loadedAt = SystemClock.elapsedRealtime()
            pollSoon()
        }
    }

    private fun updateLocks() {
        if (playWhenReady && !released) {
            wifiLock.acquire()
            wakeLock.acquire(WAKE_TIMEOUT_MS)
        } else {
            wifiLock.release()
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    /**
     * Net thread: runs [block]. No answer stops playback as unreachable. Any
     * other failure stops it as the track's failure when [loadsTrack], and
     * is only logged otherwise: the next poll shows where the renderer is.
     */
    private fun call(loadsTrack: Boolean = false, block: () -> Unit) {
        if (net.isShutdown) return
        net.execute {
            try {
                block()
            } catch (e: Exception) {
                Log.w(TAG, "${renderer.name}: ${e.message}")
                when {
                    e is Unreachable -> main.post { fail(e, PlaybackException.ERROR_CODE_REMOTE_ERROR) }
                    loadsTrack -> main.post { fail(e, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) }
                }
            }
        }
    }

    /** No answer from the renderer at all. */
    private class Unreachable(cause: Exception) : IOException(cause.message, cause)

    /** Stops with an error: [PlaybackException.ERROR_CODE_REMOTE_ERROR] when the renderer can't be reached, else the track's. */
    private fun fail(cause: Exception, code: Int) {
        loading = false
        if (released) return
        setPosition(currentPositionMs())
        playerError = PlaybackException(
            if (code == PlaybackException.ERROR_CODE_REMOTE_ERROR) "${renderer.name} didn't respond" else "${renderer.name} can't play this",
            cause, code,
        )
        playbackState = Player.STATE_IDLE
        playWhenReady = false
        loadedUrl = null
        updateLocks()
        invalidateState()
    }

    /** Sonos: the other rooms follow the lead room (x-rincon:<its id>). */
    private fun joinGroup() {
        renderer.members.forEach {
            reach(it.avTransport, AV_TRANSPORT, "SetAVTransportURI", "InstanceID" to 0,
                "CurrentURI" to "x-rincon:${renderer.id}", "CurrentURIMetaData" to "")
        }
        joined = true
    }

    private fun leaveGroup() {
        renderer.members.forEach {
            runCatching { soap(it.avTransport, AV_TRANSPORT, "BecomeCoordinatorOfStandaloneGroup", "InstanceID" to 0) }
        }
        joined = false
    }

    /** Net thread: the volume people hear; for a group, the whole group's. */
    private fun rendererVolume(): Int? = when {
        renderer.isGroup && renderer.groupRenderingControl != null -> grc("GetGroupVolume")["CurrentVolume"]?.toIntOrNull()
        renderer.renderingControl != null -> rc("GetVolume", "Channel" to "Master")["CurrentVolume"]?.toIntOrNull()
        else -> null
    }

    private fun setRendererVolume(volume: Int) {
        if (renderer.isGroup && renderer.groupRenderingControl != null) grc("SetGroupVolume", "DesiredVolume" to volume)
        else rc("SetVolume", "Channel" to "Master", "DesiredVolume" to volume)
    }

    private fun grc(action: String, vararg args: Pair<String, Any>) =
        reach(renderer.groupRenderingControl ?: error("no GroupRenderingControl"), GROUP_RENDERING_CONTROL, action, "InstanceID" to 0, *args)

    /** [soap], with no answer as [Unreachable]; a fault (an answer) passes as it is. */
    private fun reach(controlUrl: String, service: String, action: String, vararg args: Pair<String, Any>) =
        try { soap(controlUrl, service, action, *args) } catch (e: IOException) { throw Unreachable(e) }

    private fun avt(action: String, vararg args: Pair<String, Any>) =
        reach(renderer.avTransport, AV_TRANSPORT, action, "InstanceID" to 0, *args)

    private fun rc(action: String, vararg args: Pair<String, Any>) =
        reach(renderer.renderingControl ?: error("no RenderingControl"), RENDERING_CONTROL, action, "InstanceID" to 0, *args)

    private companion object {
        const val TAG = "RendererPlayer"
        const val VOLUME_STEP = 2
        const val SETTLE_MS = 4000L // renderers report stale state right after a command
        const val END_SLACK_MS = 5000L
        const val FAST_POLL_MS = 1000L
        const val SLOW_POLL_MS = 5000L
        const val MAX_MISSED_POLLS = 3
        const val WAKE_TIMEOUT_MS = 6 * 60 * 60 * 1000L
        val DONE: ListenableFuture<*> = Futures.immediateVoidFuture()
        val DEVICE_INFO = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMinVolume(0).setMaxVolume(100).build()
        val COMMANDS = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP, Player.COMMAND_RELEASE,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_CHANGE_MEDIA_ITEMS,
            Player.COMMAND_SET_SHUFFLE_MODE, Player.COMMAND_SET_REPEAT_MODE,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_DEVICE_VOLUME, Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
            Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
        ).build()
    }
}
