package com.simonpena.mako

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Lyrics from Now Playing, on a small lyrics.db written where the app reads it. Nothing here makes a sound: the songs are fake. */
@RunWith(AndroidJUnit4::class)
class LyricsUiTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var player: PlayerHolder

    private fun track(id: Long, title: String, path: String) =
        Track(id, title, "Phil Collins", "Face Value", 1, 336_000, id.toInt(), Uri.parse("content://media/external/audio/media/$id"), path)

    private val library = listOf(
        Album(1, "Face Value", "Phil Collins", 1981, listOf(
            track(100, "In the Air Tonight", "Phil Collins/Face Value/01 In the Air Tonight.mp3"),
            track(101, "This Must Be Love", "Phil Collins/Face Value/02 This Must Be Love.mp3"),
            track(102, "Droned", "Phil Collins/Face Value/05 Droned.mp3"),
        ), "Rock"),
    )

    @Before fun setUp() {
        val file = LyricsDb.file(context).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
            db.execSQL("CREATE TABLE lyrics (path TEXT PRIMARY KEY, synced TEXT, plain TEXT, instrumental INTEGER, lrclib_id INTEGER)")
            db.execSQL("INSERT INTO meta VALUES ('schema', '1')")
            db.execSQL("INSERT INTO lyrics VALUES ('Phil Collins/Face Value/01 In the Air Tonight.mp3', '[00:17.12] I can feel it coming\n[00:40.00] Coming in the air tonight', NULL, 0, 1)")
            db.execSQL("INSERT INTO lyrics VALUES ('Phil Collins/Face Value/02 This Must Be Love.mp3', NULL, 'Words without times', 0, 2)")
        }
        main { player = PlayerHolder(context) }
        compose.setContent {
            MakoApp(hasPermission = true, library = library, player = player, hidden = Hidden(context), status = null, onGrant = {}, onRescan = {})
        }
    }

    @After fun tearDown() {
        main { player.play(emptyList(), 0) }
        compose.waitUntil(5_000) { player.currentTrackId == null }
        main { player.release() }
        LyricsDb.file(context).delete()
    }

    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun nowPlaying(title: String, id: Long) {
        compose.onNodeWithText("Face Value").performClick()
        compose.onNodeWithText(title).performClick()
        compose.waitUntil(5_000) { player.currentTrackId == id }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText(title).performClick() // the mini player
    }

    private fun waitFor(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    // Tapping a line seeks there, but these fake songs never load, and a song that hasn't
    // loaded isn't seekable, so Media3 ignores the seek: checked by hand on a real song.
    @Test fun timedLyricsShowWithTheirSource() {
        nowPlaying("In the Air Tonight", 100)
        compose.onNodeWithText("I can feel it coming").assertExists() // the card below the controls
        compose.onNodeWithText("Show lyrics").performScrollTo().performClick()
        waitFor("Lyrics from LRCLIB")
        compose.onNodeWithText("Coming in the air tonight").assertExists()
        compose.onNodeWithText("Lyrics from LRCLIB").assertExists()
    }

    @Test fun wordsWithoutTimesStillShow() {
        nowPlaying("This Must Be Love", 101)
        compose.onNodeWithText("Show lyrics").performScrollTo().performClick()
        waitFor("Lyrics from LRCLIB")
        compose.onNodeWithText("Words without times").assertExists()
    }

    @Test fun aSongWithoutLyricsOffersNone() {
        nowPlaying("Droned", 102)
        compose.onNodeWithContentDescription("Up next").assertExists() // Now Playing is open
        compose.onNodeWithText("Show lyrics").assertDoesNotExist()
        compose.onNodeWithText("Lyrics").assertDoesNotExist() // no card at all
    }

    @Test fun theHeartMarksTheSongAndTheOutputRowIsItsOwnButton() {
        nowPlaying("Droned", 102)
        compose.onNodeWithContentDescription("Favourite").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithContentDescription("Favourite").performClick().assertIsOff()
        compose.onNodeWithText("Change").assertDoesNotExist()
    }
}
