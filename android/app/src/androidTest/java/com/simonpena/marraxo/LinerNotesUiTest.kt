package com.simonpena.marraxo

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The liner notes screens, on a small linernotes.db written where the app reads it. */
@RunWith(AndroidJUnit4::class)
class LinerNotesUiTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var player: PlayerHolder

    private val faceValue = "Phil Collins/Face Value/01 In the Air Tonight.mp3"
    private val sussudio = "Phil Collins/No Jacket Required/01 Sussudio.mp3"

    private fun track(id: Long, title: String, artist: String, album: String, albumId: Long, path: String) =
        Track(id, title, artist, album, albumId, 200_000, 1, Uri.parse("content://media/external/audio/media/$id"), path)

    private val library = listOf(
        Album(1, "Face Value", "Phil Collins", 1981, listOf(track(100, "In the Air Tonight", "Phil Collins", "Face Value", 1, faceValue)), "Rock"),
        Album(2, "No Jacket Required", "Phil Collins", 1985, listOf(track(200, "Sussudio", "Phil Collins", "No Jacket Required", 2, sussudio)), "Pop"),
        Album(3, "Demo", "Nobody", 2000, listOf(track(300, "Unknown Song", "Nobody", "Demo", 3, "Nobody/Demo/01 Unknown Song.mp3")), ""),
    )

    @Before fun setUp() {
        val file = LinerNotes.file(context).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            """
            CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT);
            CREATE TABLE album (id INTEGER PRIMARY KEY, mb_release TEXT, artist TEXT, title TEXT, year INTEGER,
                                label TEXT, catno TEXT, country TEXT, summary TEXT, summary_lang TEXT, wikipedia_url TEXT);
            CREATE TABLE track (path TEXT PRIMARY KEY, album_id INTEGER, title TEXT, mb_recording TEXT);
            CREATE TABLE credit (album_id INTEGER, path TEXT, role TEXT, name TEXT, detail TEXT)
            """.split(";").forEach(db::execSQL)
            db.execSQL("INSERT INTO meta VALUES ('schema', '1')")
            db.execSQL("INSERT INTO album VALUES (258, 'x', 'Phil Collins', 'Face Value', 1981, 'Atlantic', NULL, 'XE', 'Face Value is the debut solo album by Phil Collins.', 'en', NULL)")
            db.execSQL("INSERT INTO album VALUES (260, 'z', 'Phil Collins', 'No Jacket Required', 1985, NULL, NULL, NULL, NULL, NULL, NULL)")
            db.execSQL("INSERT INTO album VALUES (261, 'w', 'Phil Collins', 'But Seriously', 1989, NULL, NULL, NULL, NULL, NULL, NULL)") // not on the phone
            db.execSQL("INSERT INTO track VALUES ('$faceValue', 258, 'In the Air Tonight', 'r1')")
            db.execSQL("INSERT INTO track VALUES ('$sussudio', 260, 'Sussudio', 'r3')")
            listOf(
                "258, '$faceValue', 'instrument', 'Daryl Stuermer', 'guitar'",
                "258, '$faceValue', 'instrument', 'Violinist One', 'violin'",
                "258, '$faceValue', 'instrument', 'Violinist Two', 'violin'",
                "258, '$faceValue', 'instrument', 'Cellist One', 'cello'",
                "258, '$faceValue', 'instrument', 'Violist One', 'viola'",
                "258, '$faceValue', 'producer', 'Hugh Padgham', ''",
                "258, NULL, 'recorded at', 'The Town House', ''",
                "260, NULL, 'recorded at', 'The Town House', ''",
                "261, NULL, 'recorded at', 'The Town House', ''",
            ).forEach { db.execSQL("INSERT INTO credit VALUES ($it)") }
        }
        main { player = PlayerHolder(context) }
        compose.setContent {
            MakoApp(hasPermission = true, library = library, player = player, hidden = Hidden(context), status = null, onGrant = {}, onRescan = {})
        }
    }

    @After fun tearDown() {
        main { player.release() }
        LinerNotes.file(context).delete()
    }

    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    /** The notes load off the main thread, which the test framework doesn't wait for. */
    private fun waitFor(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(text, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun aSongsNotesShowWhoMadeItAndLeadToTheirOtherAlbums() {
        compose.onNodeWithText("Face Value").performClick()
        compose.onAllNodesWithContentDescription("Song options")[0].performClick()
        compose.onNodeWithText("Song notes").performClick()
        waitFor("Played by")
        compose.onNodeWithText("Daryl Stuermer").assertExists()
        compose.onNodeWithText("guitar").assertExists()
        compose.onNodeWithText("Daryl Stuermer").assertHasNoClickAction() // on no other album of yours
        compose.onNodeWithText("Also in your library", ignoreCase = true).assertDoesNotExist() // the names are the links
        compose.onNodeWithText("The Town House").performScrollTo().performClick()
        waitFor("Not on this phone")
        compose.onNodeWithText("But Seriously").assertHasNoClickAction()
        compose.onNodeWithText("No Jacket Required").performClick() // the place's albums, by year
        compose.onNodeWithText("Sussudio").assertExists() // the album in the library
    }

    @Test fun aStringSectionFoldsAwayAndOpensInPlace() {
        compose.onNodeWithText("Face Value").performClick()
        compose.onAllNodesWithContentDescription("Song options")[0].performClick()
        compose.onNodeWithText("Song notes").performClick()
        waitFor("Played by")
        compose.onNodeWithText("Cellist One").assertDoesNotExist()
        compose.onNodeWithText("4 string players").performClick()
        compose.onNodeWithText("Cellist One").assertExists()
    }

    @Test fun anAlbumsNotesTellItsStory() {
        compose.onNodeWithText("Face Value").performClick()
        compose.onNodeWithText("Liner notes").performClick()
        waitFor("Face Value is the debut solo album by Phil Collins.")
        compose.onNodeWithText("Wikipedia · CC BY-SA 4.0").assertExists()
        compose.onNodeWithText("Recorded at", ignoreCase = true).assertExists()
    }

    @Test fun aSongTheNotesDontKnowOffersNone() {
        compose.onNodeWithText("Demo").performClick()
        compose.onAllNodesWithContentDescription("Song options")[0].performClick()
        compose.onNodeWithText("Play next").assertExists()
        compose.onNodeWithText("Song notes").assertDoesNotExist()
        compose.onNodeWithText("Liner notes").assertDoesNotExist()
    }

    @Test fun nowPlayingShowsTheAlbumsStoryBelowAndOpensTheNotes() {
        main { player.play(library[0].tracks, 0) }
        compose.waitUntil(5_000) { player.currentTrackId == 100L }
        compose.onNodeWithText("In the Air Tonight").performClick() // the mini player
        compose.onNodeWithText("Face Value is the debut solo album by Phil Collins.").assertExists()
        compose.onNodeWithText("Show notes").performScrollTo().performClick()
        compose.onNodeWithText("Hugh Padgham").assertExists()
        main { player.play(emptyList(), 0) }
    }

    @Test fun nowPlayingHasNoNotesCardWithoutNotes() {
        main { player.play(library[2].tracks, 0) }
        compose.waitUntil(5_000) { player.currentTrackId == 300L }
        compose.onNodeWithText("Unknown Song").performClick()
        compose.onNodeWithContentDescription("Up next").assertExists()
        compose.onNodeWithText("Show notes").assertDoesNotExist()
        main { player.play(emptyList(), 0) }
    }
}
