package com.simonpena.mako

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.simonpena.mako.upnp.MediaServer
import com.simonpena.mako.upnp.Renderer
import com.simonpena.mako.upnp.RendererPlayer

/**
 * Owns the player. Runs independently of the UI, so closing screens, locking
 * the phone or pressing Home never stops playback. Media3 provides the
 * notification and lock-screen controls from the session.
 *
 * Playback goes to this phone (ExoPlayer) or to a speaker or the TV
 * ([RendererPlayer]); [SET_OUTPUT] switches between them, keeping the queue
 * and position.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var recorder: PlayRecorder? = null
    private lateinit var phone: ExoPlayer
    private var server: MediaServer? = null

    override fun onCreate() {
        super.onCreate()
        phone = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        recorder = PlayRecorder(PlayHistory(this), phone).also(phone::addListener)
        // Tapping the notification (or the lock-screen controls) opens the app.
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, phone)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(SET_OUTPUT, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction != SET_OUTPUT) {
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                    }
                    setOutput(if (args.isEmpty) null else Renderer.fromBundle(args))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                // Items from a controller arrive without their URI; restore it.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                    mediaItems.map { item ->
                        item.buildUpon()
                            .setUri(item.requestMetadata.mediaUri ?: item.localConfiguration?.uri)
                            .build()
                    }.toMutableList()
                )
            })
            .build()
    }

    /** Moves the queue and position to [renderer], or back to this phone when null. */
    private fun setOutput(renderer: Renderer?) {
        val session = session ?: return
        val old = session.player
        if (old is RendererPlayer && old.renderer == renderer) return
        if (old === phone && renderer == null) return

        val items = List(old.mediaItemCount) { old.getMediaItemAt(it) }
        val index = old.currentMediaItemIndex
        val position = old.currentPosition
        val play = old.playWhenReady
        old.stop()

        val new: Player = if (renderer == null) phone else {
            val server = server ?: MediaServer(this).also { server = it }
            RendererPlayer(this, renderer, server) { item ->
                item.mediaMetadata.extras?.getLong(EXTRA_ALBUM_ID)?.let { coverJpeg(this, it) }
            }
        }
        recorder?.attach(new, renderer?.name ?: "phone")
        if (items.isNotEmpty()) {
            new.setMediaItems(items, index, position)
            new.prepare()
        }
        new.playWhenReady = play
        session.player = new
        if (old is RendererPlayer) old.release()
        session.setSessionExtras(Bundle().apply { renderer?.let { putBundle(EXTRA_OUTPUT, it.toBundle()) } })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        recorder?.close()
        recorder = null
        session?.run {
            if (player !== phone) player.release()
            release()
        }
        phone.release()
        session = null
        server?.close()
        server = null
        super.onDestroy()
    }

    companion object {
        const val SET_OUTPUT = "com.simonpena.mako.SET_OUTPUT"
        const val EXTRA_OUTPUT = "output"
    }
}
