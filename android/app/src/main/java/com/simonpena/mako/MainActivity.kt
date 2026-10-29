package com.simonpena.mako

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var player: PlayerHolder
    private var hasPermission by mutableStateOf(false)
    private var library by mutableStateOf<List<Album>?>(null)

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
        refreshPermission()
        setContent {
            MakoApp(
                hasPermission = hasPermission,
                library = library,
                player = player,
                onGrant = { request.launch(permissions) },
                onRescan = { loadLibraryAsync() },
            )
        }
    }

    private fun refreshPermission() {
        hasPermission = ContextCompat.checkSelfPermission(this, permissions[0]) == PackageManager.PERMISSION_GRANTED
        if (hasPermission && library == null) loadLibraryAsync()
    }

    private fun loadLibraryAsync() {
        lifecycleScope.launch { library = loadLibrary(this@MainActivity) }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
