package com.simonpena.mako.upnp

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.simonpena.mako.idle
import com.simonpena.mako.toMediaItem
import com.simonpena.mako.track
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/** RendererPlayer and MediaServer against a fake Sonos or TV on localhost. */
@RunWith(RobolectricTestRunner::class)
class RendererPlayerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val server = MediaServer(context)
    private val cleanup = mutableListOf<() -> Unit>({ server.close() })

    @After fun tearDown() = cleanup.forEach { it() }

    private fun file(name: String, size: Int = 300_000): File =
        tmp.newFile(name).apply { writeBytes(Random(name.hashCode()).nextBytes(size)) }

    private fun item(id: Long, title: String, f: File, durationMs: Long): MediaItem =
        track(id, title, durationMs = durationMs).copy(uri = Uri.fromFile(f)).toMediaItem()

    private fun start(kind: FakeRenderer.Kind, random: Random = Random(1)): Pair<FakeRenderer, RendererPlayer> {
        val fake = FakeRenderer(kind)
        val player = RendererPlayer(context, fake.renderer, server, random) { null }
        cleanup += { player.release(); fake.close() }
        return fake to player
    }

    /** Lets fake time pass and background calls finish until [condition] holds. */
    private fun eventually(what: String, condition: () -> Boolean) {
        repeat(150) {
            if (condition()) return
            idle(200)
            Thread.sleep(10)
        }
        throw AssertionError("timed out waiting for: $what")
    }

    @Test fun `hands over at the phone's position and serves the whole file`() {
        val (fake, player) = start(FakeRenderer.Kind.SONOS)
        val f = file("saya.mp3")
        player.setMediaItems(listOf(item(1, "O... Saya", f, 215_000)), 0, 30_000)
        player.prepare()
        player.play()
        eventually("the speaker plays") { fake.state == "PLAYING" && player.isPlaying }
        assertEquals(1, fake.actions.count { it == "SetAVTransportURI" }) // loaded once, not again by play()
        assertArrayEquals(f.readBytes(), fake.downloaded) // Sonos downloads it all up front
        assertTrue(fake.uri.matches(Regex("http://127\\.0\\.0\\.1:\\d+/t/[0-9a-f]{18}\\.mp3")))
        assertEquals(30_000L, fake.positionMs) // continued where the phone was
        assertTrue("<dc:title>O... Saya</dc:title>" in unescape(fake.metadata))
    }

    @Test fun `moves to the next track when the renderer finishes one`() {
        val (fake, player) = start(FakeRenderer.Kind.SONOS)
        val transitions = mutableListOf<Int>()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { transitions += reason }
        })
        player.setMediaItems(listOf(item(1, "O... Saya", file("a.mp3"), 215_000), item(2, "Riots", file("b.mp3"), 121_000)))
        player.prepare()
        player.play()
        eventually("first track plays") { fake.state == "PLAYING" && player.isPlaying }
        val first = fake.uri
        idle(5_000) // past the settling time after a load
        fake.positionMs = 214_000
        eventually("the player sees the end coming") { player.currentPosition >= 214_000 }
        fake.finishTrack()
        eventually("second track loads") { player.currentMediaItemIndex == 1 && fake.uri != first && fake.state == "PLAYING" }
        assertTrue("<dc:title>Riots</dc:title>" in unescape(fake.metadata))
        // Play history counts this as "completed" only if the reason is AUTO.
        assertEquals(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, transitions.last())
    }

    private fun album(n: Int) = (1..n).map { item(it.toLong(), "Song $it", file("s$it.mp3", 20_000), 200_000) }

    /** Plays the current track to its end on the fake, the way the earlier test does. */
    private fun finishCurrentTrack(fake: FakeRenderer, player: RendererPlayer) {
        eventually("playing") { fake.state == "PLAYING" && player.isPlaying }
        idle(5_000)
        fake.positionMs = 199_000
        eventually("near the end") { player.currentPosition >= 199_000 }
        fake.finishTrack()
    }

    private fun titleOnSpeaker(fake: FakeRenderer) = Regex("<dc:title>([^<]*)").find(unescape(fake.metadata))?.groupValues?.get(1)

    @Test fun `repeat one plays the same track again`() {
        val (fake, player) = start(FakeRenderer.Kind.SONOS)
        player.setMediaItems(album(3))
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.prepare()
        player.play()
        finishCurrentTrack(fake, player)
        eventually("loaded again") { fake.actions.count { it == "SetAVTransportURI" } == 2 && fake.state == "PLAYING" }
        assertEquals(0, player.currentMediaItemIndex)
        assertEquals("Song 1", titleOnSpeaker(fake))
    }

    @Test fun `repeat all goes back to the first track after the last`() {
        val (fake, player) = start(FakeRenderer.Kind.SONOS)
        player.setMediaItems(album(2), 1, 0)
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.prepare()
        player.play()
        finishCurrentTrack(fake, player)
        eventually("first track") { player.currentMediaItemIndex == 0 && titleOnSpeaker(fake) == "Song 1" && fake.state == "PLAYING" }
    }

    @Test fun `without repeat the album ends after the last track`() {
        val (fake, player) = start(FakeRenderer.Kind.SONOS)
        player.setMediaItems(album(2), 1, 0)
        player.prepare()
        player.play()
        finishCurrentTrack(fake, player)
        eventually("ended") { player.playbackState == Player.STATE_ENDED }
    }

    @Test fun `shuffle plays every track once, in a different order, for next and track ends alike`() {
        val (fake, player) = start(FakeRenderer.Kind.SONOS)
        player.setMediaItems(album(6))
        player.shuffleModeEnabled = true
        player.prepare()
        player.play()
        eventually("first plays") { fake.state == "PLAYING" && titleOnSpeaker(fake) != null }
        val heard = mutableListOf(titleOnSpeaker(fake)!!)
        repeat(3) {
            player.seekToNext()
            eventually("next loads") { titleOnSpeaker(fake) !in heard && fake.state == "PLAYING" }
            heard += titleOnSpeaker(fake)!!
        }
        repeat(2) {
            finishCurrentTrack(fake, player)
            eventually("next by itself") { titleOnSpeaker(fake) !in heard && fake.state == "PLAYING" }
            heard += titleOnSpeaker(fake)!!
        }
        val album = (1..6).map { "Song $it" }
        assertEquals(album.toSet(), heard.toSet()) // all six, none twice
        assertTrue("shuffled, not in album order: $heard", heard != album)
    }

    @Test fun `turning shuffle off restores album order from the current track`() {
        val (fake, player) = start(FakeRenderer.Kind.SONOS)
        player.setMediaItems(album(6))
        player.shuffleModeEnabled = true
        player.prepare()
        player.play()
        eventually("plays") { fake.state == "PLAYING" }
        player.seekToNext()
        eventually("second loads") { fake.actions.count { it == "SetAVTransportURI" } == 2 && fake.state == "PLAYING" }
        val current = titleOnSpeaker(fake)!!
        player.shuffleModeEnabled = false
        assertEquals((1..6).map { "Song $it" }, (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaMetadata.title })
        assertEquals(current, player.currentMediaItem?.mediaMetadata?.title) // still on the same song
    }

    @Test fun `shuffle and repeat can be turned on`() {
        val (_, player) = start(FakeRenderer.Kind.SONOS)
        assertTrue(player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE))
    }

    private fun group(): Triple<FakeRenderer, FakeRenderer, RendererPlayer> {
        val lead = FakeRenderer(FakeRenderer.Kind.SONOS, "Cocina")
        val member = FakeRenderer(FakeRenderer.Kind.SONOS, "Estudio")
        val renderer = lead.renderer.copy(name = "Cocina + Estudio", model = "Sonos group", members = listOf(member.renderer))
        val player = RendererPlayer(context, renderer, server, Random(1)) { null }
        cleanup += { player.release(); lead.close(); member.close() }
        return Triple(lead, member, player)
    }

    @Test fun `a group plays on the lead room with the others joined to it`() {
        val (lead, member, player) = group()
        player.setMediaItems(listOf(item(1, "Riots", file("b.mp3"), 121_000)))
        player.prepare()
        player.play()
        eventually("the lead plays") { lead.state == "PLAYING" }
        assertEquals("RINCON_Cocina", member.joinedTo)
        assertTrue(member.actions.indexOf("SetAVTransportURI") >= 0)
        assertEquals("", member.uri) // the member never gets the track itself
    }

    @Test fun `leaving a group sends every room back on its own`() {
        val (lead, member, player) = group()
        player.setMediaItems(listOf(item(1, "Riots", file("b.mp3"), 121_000)))
        player.prepare()
        player.play()
        eventually("joined") { member.joinedTo != null && lead.state == "PLAYING" }
        player.release()
        eventually("left") { member.joinedTo == null }
    }

    @Test fun `a group's volume is the group volume`() {
        val (lead, _, player) = group()
        player.setMediaItems(listOf(item(1, "Riots", file("b.mp3"), 121_000)))
        player.prepare()
        eventually("polled") { player.deviceVolume == 20 }
        player.increaseDeviceVolume(0)
        eventually("group louder") { lead.groupVolume == 22 }
        assertEquals(15, lead.volume) // the lead room alone is untouched
    }

    @Test fun `a track end is recognised even if the speaker reports 0 just before stopping`() {
        // Seen on Estudio: at the end of a track Sonos briefly said PLAYING at 0:00, then STOPPED.
        val (fake, player) = start(FakeRenderer.Kind.SONOS)
        player.setMediaItems(album(2))
        player.prepare()
        player.play()
        eventually("playing") { fake.state == "PLAYING" && player.isPlaying }
        idle(5_000)
        fake.positionMs = 199_000
        eventually("near the end") { player.currentPosition >= 199_000 }
        fake.positionMs = 0 // still PLAYING, position already reset
        eventually("the player saw 0") { player.currentPosition < 5_000 }
        fake.state = "STOPPED"
        eventually("moves on to the next track") { player.currentMediaItemIndex == 1 && titleOnSpeaker(fake) == "Song 2" }
        assertTrue(player.playWhenReady)
    }

    @Test fun `a track shorter than Android says still counts as ended`() {
        // Seen on Estudio: Arregla Los Papeles stopped with Sonos reporting 3:23 of 3:35. For VBR
        // MP3s Sonos's position drifts behind the audio, so the end comes before the reported length.
        val (fake, player) = start(FakeRenderer.Kind.SONOS)
        player.setMediaItems(listOf(item(1, "Long", file("l.mp3", 20_000), 215_301), item(2, "Next", file("n.mp3", 20_000), 200_000)))
        player.prepare()
        player.play()
        eventually("playing") { fake.state == "PLAYING" && player.isPlaying }
        idle(5_000)
        fake.positionMs = 203_000
        eventually("near the real end") { player.currentPosition >= 203_000 }
        fake.positionMs = 0
        fake.state = "STOPPED"
        eventually("moves on") { player.currentMediaItemIndex == 1 && titleOnSpeaker(fake) == "Next" }
    }

    @Test fun `a stop well before the end is still someone stopping it`() {
        val (fake, player) = start(FakeRenderer.Kind.SONOS)
        player.setMediaItems(album(2))
        player.prepare()
        player.play()
        eventually("playing") { fake.state == "PLAYING" && player.isPlaying }
        idle(5_000)
        fake.positionMs = 120_000 // 2:00 of 3:20
        eventually("mid-track") { player.currentPosition >= 120_000 }
        fake.positionMs = 0
        fake.state = "STOPPED"
        eventually("pauses here") { !player.playWhenReady }
        assertEquals(0, player.currentMediaItemIndex)
    }

    @Test fun `follows a pause made elsewhere`() {
        val (fake, player) = start(FakeRenderer.Kind.SONOS)
        player.setMediaItems(listOf(item(1, "Riots", file("b.mp3"), 121_000)))
        player.prepare()
        player.play()
        eventually("plays") { player.isPlaying }
        idle(5_000)
        fake.state = "PAUSED_PLAYBACK"
        eventually("pauses here too") { !player.playWhenReady }
    }

    @Test fun `steps aside when another controller takes over, and takes back on play`() {
        val (fake, player) = start(FakeRenderer.Kind.SONOS)
        player.setMediaItems(listOf(item(1, "Riots", file("b.mp3"), 121_000)))
        player.prepare()
        player.play()
        eventually("plays") { player.isPlaying }
        val ours = fake.uri
        idle(5_000)
        fake.takeOver("x-rincon-queue:RINCON_000E58D4E5F601400#2")
        eventually("stops claiming to play") { !player.playWhenReady }
        assertTrue(fake.uri.startsWith("x-rincon")) // and leaves the speaker alone
        player.play()
        eventually("takes the speaker back") { fake.uri == ours && fake.state == "PLAYING" }
    }

    @Test fun `the TV gets DLNA features, so it can seek`() {
        val (fake, player) = start(FakeRenderer.Kind.TV)
        player.setMediaItems(listOf(item(1, "Daily Growing", file("c.mp3"), 293_000)))
        player.prepare()
        player.play()
        eventually("plays") { fake.state == "PLAYING" }
        assertEquals(DLNA_FEATURES, fake.contentFeatures)
        idle(5_000)
        player.seekTo(90_000)
        eventually("seeks") { fake.positionMs == 90_000L }
        assertEquals(null, player.playerError)
    }

    @Test fun `waits for a slow TV to start without giving up`() {
        val (fake, player) = start(FakeRenderer.Kind.TV)
        fake.slowStart = true // the real TV took 8.4 s from Play to PLAYING
        player.setMediaItems(listOf(item(1, "Daily Growing", file("c.mp3"), 293_000)))
        player.prepare()
        player.play()
        eventually("Play reaches the TV") { "Play" in fake.actions }
        idle(10_000) // longer than the player's settling time
        eventually("buffering while it starts") { player.playbackState == Player.STATE_BUFFERING }
        assertTrue(player.playWhenReady)
        assertEquals(null, player.playerError)
        fake.state = "PLAYING"
        eventually("plays") { player.isPlaying }
    }

    @Test fun `a slow answer to Play is not mistaken for being stopped`() {
        val (fake, player) = start(FakeRenderer.Kind.TV)
        fake.playDelayMs = 500 // meanwhile the fake clock runs far past the settling time
        fake.stateAfterPlay = "STOPPED" // and the TV still says STOPPED for a moment
        player.setMediaItems(listOf(item(1, "Daily Growing", file("c.mp3"), 293_000)))
        player.prepare()
        player.play()
        while (fake.plays == 0) { idle(1_000); Thread.sleep(50) }
        repeat(2) { idle(1_000); Thread.sleep(100) } // a couple of polls see STOPPED
        fake.state = "PLAYING"
        eventually("plays") { player.isPlaying }
    }

    @Test fun `volume goes both ways`() {
        val (fake, player) = start(FakeRenderer.Kind.SONOS)
        player.setMediaItems(listOf(item(1, "Riots", file("b.mp3"), 121_000)))
        player.prepare()
        eventually("polled once") { player.deviceVolume == 15 }
        player.increaseDeviceVolume(0)
        eventually("speaker louder") { fake.volume == 17 }
        fake.volume = 40 // changed in the Sonos app
        eventually("phone follows") { player.deviceVolume == 40 }
    }

    @Test fun `the server only serves what was published`() {
        val f = file("d.mp3", 1_000)
        val (path, _) = server.publishTrack("9", Uri.fromFile(f))
        val base = server.baseUrlFor("127.0.0.1")
        fun status(p: String, range: String? = null) = (URL(base + p).openConnection() as HttpURLConnection).run {
            range?.let { setRequestProperty("Range", it) }
            responseCode.also { disconnect() }
        }
        assertEquals(200, status(path))
        assertEquals(206, status(path, "bytes=100-"))
        assertEquals(416, status(path, "bytes=5000-"))
        assertEquals(404, status("/t/notatoken.mp3"))
        assertEquals(404, status("/../../etc/hosts"))
        assertEquals(404, status(f.absolutePath)) // never a filesystem path
    }
}
