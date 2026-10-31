package com.simonpena.mako

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
            db.execSQL("INSERT INTO track VALUES ('$faceValue', 258, 'In the Air Tonight', 'r1')")
            db.execSQL("INSERT INTO track VALUES ('$sussudio', 260, 'Sussudio', 'r3')")
            listOf(
                "258, '$faceValue', 'instrument', 'Daryl Stuermer', 'guitar'",
                "258, '$faceValue', 'producer', 'Hugh Padgham', ''",
                "258, NULL, 'recorded at', 'The Town House', ''",
                "260, NULL, 'recorded at', 'The Town House', ''",
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

    @Test fun aSongsNotesShowWhoMadeItAndLeadToTheirOtherAlbums() {
        compose.onNodeWithText("Face Value").performClick()
        compose.onAllNodesWithContentDescription("Song options")[0].performClick()
        compose.onNodeWithText("Song notes").performClick()
        compose.onNodeWithText("Played by", ignoreCase = true).assertExists()
        compose.onNodeWithText("Daryl Stuermer").assertExists()
        compose.onNodeWithText("guitar").assertExists()
        compose.onNodeWithText("Also in your library", ignoreCase = true).performScrollTo()
        compose.onNodeWithText("1 more album · Phil Collins").performClick() // The Town House
        compose.onNodeWithText("No Jacket Required").performClick() // the place's albums, by year
        compose.onNodeWithText("Sussudio").assertExists() // the album in the library
    }

    @Test fun anAlbumsNotesTellItsStory() {
        compose.onNodeWithText("Face Value").performClick()
        compose.onNodeWithText("Liner notes").performClick()
        compose.onNodeWithText("Face Value is the debut solo album by Phil Collins.").assertExists()
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
}
