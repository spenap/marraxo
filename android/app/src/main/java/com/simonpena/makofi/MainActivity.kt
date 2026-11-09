package com.simonpena.makofi

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var player: PlayerHolder
    private lateinit var hidden: Hidden
    private var hasPermission by mutableStateOf(false)
    private var library by mutableStateOf<List<Album>?>(null)
    private var status by mutableStateOf<String?>(null)
    /** The latest plays, for the Home tab. */
    private var plays by mutableStateOf(emptyList<Play>())
    private val history by lazy { PlayHistory(this) }
    private var isHome by mutableStateOf(false)
    private var homePresses by mutableIntStateOf(0)
    /** The file opened from another app: "" until it's read; null after the screen is recreated. */
    private var opened by mutableStateOf<String?>(null)

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
        // "Open with" from another app: Now Playing for that file alone, and Back returns to that app.
        if (intent.action == Intent.ACTION_VIEW) {
            if (savedInstanceState == null) {
                opened = ""
                open(intent)
            }
            setContent { OpenedFileScreen(player, opened, onClose = ::finish) }
            return
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
                plays = plays,
            )
        }
        // A song heard is a new row: Home's Continue follows.
        lifecycleScope.launch {
            snapshotFlow { player.currentTrackId }.collect {
                delay(1000)
                if (library != null) plays = readPlays()
            }
        }
    }

    private suspend fun readPlays() = withContext(Dispatchers.IO) { runCatching { history.recent(500) }.getOrDefault(emptyList()) }

    override fun onResume() {
        super.onResume()
        isHome = isHomeApp(this)
        hideNavigationBar(this, isHome)
    }

    /** As the home screen, pressing Home while Makofi is open goes back to the library. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
        open(intent)
    }

    /** Plays the file in place of the queue; closes if it can't be read. */
    private fun open(intent: Intent) {
        val uri = intent.data?.takeIf { intent.action == Intent.ACTION_VIEW } ?: return
        lifecycleScope.launch {
            val item = readOpenedFile(this@MainActivity, uri) ?: return@launch finish()
            opened = item.mediaId
            player.open(item)
        }
    }

    private fun refreshPermission() {
        hasPermission = ContextCompat.checkSelfPermission(this, permissions[0]) == PackageManager.PERMISSION_GRANTED
        if (hasPermission && library == null) loadLibraryAsync()
    }

    private fun loadLibraryAsync() {
        lifecycleScope.launch {
            plays = readPlays() // first: the library opens on Home if there are any
            library = loadLibrary(this@MainActivity)
        }
    }

    private fun rescan() {
        lifecycleScope.launch {
            status = getString(R.string.rescan_started)
            val result = rescanMusicFolder(this@MainActivity) { scanned, total ->
                status = getString(R.string.rescan_progress, scanned, resources.getQuantityString(R.plurals.files, total, total))
            }
            library = loadLibrary(this@MainActivity)
            status = rescanSummary(resources, result)
            delay(4000)
            status = null
        }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
