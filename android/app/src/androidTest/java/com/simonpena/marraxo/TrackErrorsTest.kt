package com.simonpena.marraxo

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The phone's player skips files it can't play. The one that plays is silence. */
@RunWith(AndroidJUnit4::class)
class TrackErrorsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var player: PlayerHolder

    @Before fun setUp() {
        main { player = PlayerHolder(context) }
    }

    @After fun tearDown() {
        main { player.play(emptyList(), 0) }
        main { player.release() }
    }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun file(name: String, bytes: ByteArray): Uri =
        Uri.fromFile(File(context.cacheDir, name).apply { writeBytes(bytes) })

    private fun track(id: Long, uri: Uri) = Track(id, "Song $id", "Artist", "Album", 1, 5_000, id.toInt(), uri)

    @Test fun skipsACorruptAndAMissingFileToOneThatPlays() {
        val corrupt = file("corrupt.mp3", ByteArray(50_000) { (it * 31).toByte() })
        val missing = Uri.fromFile(File(context.cacheDir, "missing.mp3"))
        val end = SystemClock.elapsedRealtime() + 10_000
        while (player.currentTrackId != 3L || !player.isPlaying) {
            if (SystemClock.elapsedRealtime() > end) throw AssertionError("not playing the third: on ${player.currentTrackId}")
            if (player.currentTrackId == null) main { player.play(listOf(track(1, corrupt), track(2, missing), track(3, silence)), 0) }
            Thread.sleep(200)
        }
    }
}
