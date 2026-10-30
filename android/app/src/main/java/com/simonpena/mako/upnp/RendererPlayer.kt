package com.simonpena.mako.upnp

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
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * A Media3 player that plays on a UPnP renderer instead of the phone.
 *
 * The queue stays on the phone; the renderer gets one track at a time from
 * [MediaServer] and is polled every second for its state. Media3 treats it
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
        playbackState = Player.STATE_BUFFERING
        loadedAt = SystemClock.elapsedRealtime()
        loading = true
        val host = java.net.URL(renderer.avTransport).host
        val meta = item.mediaMetadata
        call {
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

    private val poll = object : Runnable {
        override fun run() {
            if (released) return
            if (!polling) {
                polling = true
                net.execute {
                    val transport = runCatching { avt("GetTransportInfo") }.getOrNull()
                    val position = runCatching { avt("GetPositionInfo") }.getOrNull()
                    val volume = runCatching { rendererVolume() }.getOrNull()
                    main.post {
                        polling = false
                        applyRemote(transport, position, volume)
                    }
                }
            }
            main.postDelayed(this, 1000)
        }
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
                    parseUpnpTime(position?.get("RelTime"))?.let(::setPosition)
                    if (!playWhenReady) { playWhenReady = true; updateLocks() } // resumed elsewhere
                }
            }
            "PAUSED_PLAYBACK" -> {
                playbackState = Player.STATE_READY
                if (!settling) {
                    parseUpnpTime(position?.get("RelTime"))?.let(::setPosition)
                    if (playWhenReady) { playWhenReady = false; updateLocks() } // paused elsewhere
                }
            }
            "TRANSITIONING" -> playbackState = Player.STATE_BUFFERING
            "STOPPED", "NO_MEDIA_PRESENT" -> if (!settling && playWhenReady) {
                val duration = items.getOrNull(index)?.mediaMetadata?.durationMs ?: Long.MAX_VALUE
                if (lastPosition >= duration - END_SLACK_MS) advance() else {
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
        main.post { loadedAt = SystemClock.elapsedRealtime() }
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

    private fun call(block: () -> Unit) {
        if (net.isShutdown) return
        net.execute {
            try {
                block()
            } catch (e: Exception) {
                Log.w(TAG, "${renderer.name}: ${e.message}")
                main.post {
                    loading = false
                    if (released) return@post
                    playerError = PlaybackException(
                        "${renderer.name} didn't respond", e, PlaybackException.ERROR_CODE_REMOTE_ERROR,
                    )
                    playbackState = Player.STATE_IDLE
                    playWhenReady = false
                    loadedUrl = null
                    updateLocks()
                    invalidateState()
                }
            }
        }
    }

    /** Sonos: the other rooms follow the lead room (x-rincon:<its id>). */
    private fun joinGroup() {
        renderer.members.forEach {
            soap(it.avTransport, AV_TRANSPORT, "SetAVTransportURI", "InstanceID" to 0,
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
        soap(renderer.groupRenderingControl ?: error("no GroupRenderingControl"), GROUP_RENDERING_CONTROL, action, "InstanceID" to 0, *args)

    private fun avt(action: String, vararg args: Pair<String, Any>) =
        soap(renderer.avTransport, AV_TRANSPORT, action, "InstanceID" to 0, *args)

    private fun rc(action: String, vararg args: Pair<String, Any>) =
        soap(renderer.renderingControl ?: error("no RenderingControl"), RENDERING_CONTROL, action, "InstanceID" to 0, *args)

    private companion object {
        const val TAG = "RendererPlayer"
        const val VOLUME_STEP = 2
        const val SETTLE_MS = 4000L // renderers report stale state right after a command
        const val END_SLACK_MS = 5000L
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
