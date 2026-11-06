package com.simonpena.makofi

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayRecorderTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val history = PlayHistory(context)
    private val player = FakePlayer()
    private val recorder = PlayRecorder(history, player).also(player::addListener)
    private val saya = track(11, "O... Saya", durationMs = 215_876)
    private val riots = track(12, "Riots", durationMs = 121_556)
    private val mausam = track(13, "Mausam & Escape", durationMs = 234_869)

    private data class Row(val title: String, val durationMs: Long?, val listenedMs: Long, val ended: String, val output: String)

    /** Rows are written on a background thread; wait for [count] of them. */
    private fun rows(count: Int): List<Row> {
        repeat(100) {
            val rows = history.readableDatabase.rawQuery("SELECT title, duration_ms, listened_ms, ended, output FROM plays ORDER BY id", null).use { c ->
                buildList { while (c.moveToNext()) add(Row(c.getString(0), if (c.isNull(1)) null else c.getLong(1), c.getLong(2), c.getString(3), c.getString(4))) }
            }
            if (rows.size >= count) return rows
            Thread.sleep(20)
        }
        error("expected $count rows")
    }

    private fun start(vararg tracks: Track) {
        player.setMediaItems(tracks.map { it.toMediaItem() })
        player.prepare()
        player.play()
        idle()
    }

    @Test fun `a track that ends by itself is completed, with its own duration`() {
        start(saya, riots)
        idle(30_000)
        player.finishTrack()
        idle()
        // The bug this guards: reading player.duration here gave the next track's length.
        assertEquals(Row("O... Saya", 215_876, 30_000, "completed", "phone"), rows(1)[0])
    }

    @Test fun `next is a skip, and pauses don't count as listening`() {
        start(saya, riots)
        idle(5_000)
        player.pause()
        idle(60_000)
        player.play()
        idle(2_000)
        player.seekToNextMediaItem()
        idle()
        assertEquals(Row("O... Saya", 215_876, 7_000, "skipped", "phone"), rows(1)[0])
    }

    @Test fun `tracks never heard are not recorded`() {
        player.setMediaItems(listOf(saya, riots, mausam).map { it.toMediaItem() })
        player.prepare()
        idle()
        player.seekToNextMediaItem() // skipped through without playing
        idle()
        player.play()
        idle(4_000)
        recorder.close()
        assertEquals(listOf(Row("Riots", 121_556, 4_000, "stopped", "phone")), rows(1))
    }

    @Test fun `moving to a speaker ends the phone's play and records the speaker's`() {
        start(saya)
        idle(3_000)
        val speaker = FakePlayer()
        player.stop()
        recorder.attach(speaker, "Estudio")
        speaker.setMediaItems(listOf(saya.toMediaItem()), 0, 3_000)
        speaker.prepare()
        speaker.play()
        idle(10_000)
        recorder.close()
        val rows = rows(2)
        assertEquals("phone", rows[0].output)
        assertEquals(3_000, rows[0].listenedMs)
        assertEquals(Row("O... Saya", 215_876, 10_000, "stopped", "Estudio"), rows[1])
    }

    @Test fun `a file opened from another app is recorded by its tags, without a MediaStore id`() {
        val file = openedItem(Uri.parse("content://com.example.files/Voice%20note.mp3"), Tags("Voice note", "", "", 60_000))
        player.setMediaItems(listOf(file, saya.toMediaItem()))
        player.prepare()
        player.play()
        idle(5_000)
        player.seekToNextMediaItem()
        idle(1_000)
        recorder.close()
        assertEquals(Row("Voice note", 60_000, 5_000, "skipped", "phone"), rows(2)[0])
        val ids = history.readableDatabase.rawQuery("SELECT media_id FROM plays ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        assertEquals(listOf(null, "11"), ids)
    }
}
