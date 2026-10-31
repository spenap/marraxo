package com.simonpena.mako

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Mako's Now Playing over the lock screen, as on an iPod (roadmap A6).
 * Android 8–9 lock screens show the session's cover as a wallpaper, but the
 * system shrinks it to 320 dp (640 px on the Nexus 4) and then stretches it
 * to fill the screen. Android 10+ shows the cover only in the media controls,
 * so the S25 keeps the system lock screen.
 */
fun shouldShowLockPlayer(sdk: Int, isHome: Boolean, hasMusic: Boolean): Boolean =
    sdk < Build.VERSION_CODES.Q && isHome && hasMusic

fun showLockPlayer(context: Context) {
    context.startActivity(Intent(context, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Shown over the lock screen (manifest: showWhenLocked). Close or Back unlocks. */
class LockActivity : ComponentActivity() {
    private lateinit var player: PlayerHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        player = PlayerHolder(this)
        lifecycleScope.launch {
            snapshotFlow { player.output }.collect {
                volumeControlStream = if (it == null) AudioManager.STREAM_MUSIC else AudioManager.USE_DEFAULT_STREAM_TYPE
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = unlock()
        })
        setContent {
            val track = player.currentItem?.toTrack()
            if (track == null) {
                Box(Modifier.fillMaxSize().background(GDeepest))
            } else {
                val album = Album(track.albumId, track.album, track.artist, 0, listOf(track))
                NowPlaying(album, track, player, onClose = ::unlock)
            }
        }
    }

    /** Asks for the unlock (instant without a PIN), then leaves Mako's home screen underneath. */
    private fun unlock() {
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
        finish()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
