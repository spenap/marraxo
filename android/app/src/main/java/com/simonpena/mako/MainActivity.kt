package com.simonpena.mako

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var player: PlayerHolder
    private lateinit var hidden: Hidden
    private var hasPermission by mutableStateOf(false)
    private var library by mutableStateOf<List<Album>?>(null)
    private var status by mutableStateOf<String?>(null)
    private var isHome by mutableStateOf(false)
    private var homePresses by mutableIntStateOf(0)

    /** Android 13+ splits storage access by media type and adds a notification permission. */
    private val permissions =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private val request = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        player = PlayerHolder(this)
        hidden = Hidden(this)
        // Volume keys: music volume on this phone, even when paused. On a
        // speaker or the TV, let the system send them to the session, which
        // sets the renderer's volume.
        lifecycleScope.launch {
            snapshotFlow { player.output }.collect {
                volumeControlStream = if (it == null) AudioManager.STREAM_MUSIC else AudioManager.USE_DEFAULT_STREAM_TYPE
            }
        }
        refreshPermission()
        setContent {
            MakoApp(
                hasPermission = hasPermission,
                library = library,
                player = player,
                hidden = hidden,
                onGrant = { request.launch(permissions) },
                status = status,
                isHome = isHome,
                homePresses = homePresses,
                onAndroidSettings = { startActivity(Intent(Settings.ACTION_SETTINGS)) },
                onOtherApps = { openOtherLauncher(this) },
                onRescan = ::rescan,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isHome = isHomeApp(this)
    }

    /** As the home screen, pressing Home while Mako is open goes back to the library. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }

    private fun refreshPermission() {
        hasPermission = ContextCompat.checkSelfPermission(this, permissions[0]) == PackageManager.PERMISSION_GRANTED
        if (hasPermission && library == null) loadLibraryAsync()
    }

    private fun loadLibraryAsync() {
        lifecycleScope.launch { library = loadLibrary(this@MainActivity) }
    }

    private fun rescan() {
        lifecycleScope.launch {
            status = "Rescanning the Music folder…"
            val result = rescanMusicFolder(this@MainActivity)
            library = loadLibrary(this@MainActivity)
            status = when {
                result.added == 0 && result.removed == 0 -> "Library is up to date"
                else -> listOfNotNull(
                    result.added.takeIf { it > 0 }?.let { "${files(it)} added" },
                    result.removed.takeIf { it > 0 }?.let { "${files(it)} removed" },
                ).joinToString(", ", prefix = "Library updated: ")
            }
            delay(4000)
            status = null
        }
    }

    private fun files(n: Int) = if (n == 1) "1 file" else "$n files"

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
