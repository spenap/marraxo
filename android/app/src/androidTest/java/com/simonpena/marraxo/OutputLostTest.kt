package com.simonpena.marraxo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simonpena.marraxo.upnp.FakeRenderer
import com.simonpena.marraxo.upnp.Renderer
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A speaker or TV that can't be reached hands playback back to this phone. Nothing plays: the queue is empty. */
@RunWith(AndroidJUnit4::class)
class OutputLostTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fake = FakeRenderer(FakeRenderer.Kind.TV)
    private lateinit var player: PlayerHolder

    @Before fun setUp() {
        main { player = PlayerHolder(context) }
    }

    @After fun tearDown() {
        main { player.playOn(null) }
        main { player.release() }
        fake.close()
    }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).close()

    private fun waitFor(what: String, timeoutMs: Long, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (!condition()) {
            if (SystemClock.elapsedRealtime() > end) throw AssertionError("timed out waiting for: $what")
            Thread.sleep(200)
        }
    }

    /** Asks again until the controller has connected and the service has switched. */
    private fun playOn(renderer: Renderer) = waitFor("playing on ${renderer.name}", 5_000) {
        main { player.playOn(renderer) }
        player.output == renderer
    }

    @Test fun aSpeakerThatStopsAnsweringHandsBackToThePhone() {
        playOn(fake.renderer)
        fake.close()
        waitFor("back on the phone", 30_000) { player.output == null }
    }

    /** Only on an emulator: on a phone, Wi-Fi off could cut a wireless adb connection. */
    @Test fun leavingTheWifiHandsBackToThePhone() {
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        assumeTrue(connectivity.allNetworks.any { connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true })
        playOn(fake.renderer) // on localhost: still answering after the Wi-Fi goes
        try {
            shell("svc wifi disable")
            waitFor("back on the phone", 10_000) { player.output == null }
        } finally {
            shell("svc wifi enable")
        }
    }
}
