package com.simonpena.makofi

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A file opened from another app, outside the library. Nothing here makes a sound: the file is fake. */
@RunWith(AndroidJUnit4::class)
class OpenFileUiTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var player: PlayerHolder

    private val library = listOf(
        Album(1, "Face Value", "Phil Collins", 1981, listOf(
            Track(100, "In the Air Tonight", "Phil Collins", "Face Value", 1, 336_000, 1, Uri.parse("content://media/external/audio/media/100")),
        )),
    )
    private val file = openedItem(Uri.parse("content://com.example.files/Voice%20note.mp3"), Tags("Voice note", "", "", 60_000))

    @Before fun setUp() {
        main { player = PlayerHolder(context) }
    }

    @After fun tearDown() {
        main { player.play(emptyList(), 0) }
        compose.waitUntil(5_000) { player.currentItem == null }
        main { player.release() }
    }

    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun waitFor(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun anOpenedFileShowsOnlyNowPlayingAndClosesInOneStep() {
        var closed = false
        compose.setContent { OpenedFileScreen(player, file.mediaId) { closed = true } }
        main { player.play(library[0].tracks, 0) }
        compose.waitUntil(5_000) { player.currentTrackId == 100L }
        main { player.cycleRepeat() } // repeat on, left from the previous queue
        compose.waitUntil(5_000) { player.repeatMode != Player.REPEAT_MODE_OFF }
        main { player.open(file) }
        waitFor("PLAYING A FILE")
        compose.onNodeWithText("Voice note").assertExists()
        compose.onNodeWithContentDescription("Favourite").assertDoesNotExist()
        compose.onNodeWithContentDescription("Song options").assertDoesNotExist()
        // One song: play or pause it, nothing to shuffle, skip or repeat.
        compose.onNodeWithContentDescription("Play or pause").assertExists()
        for (control in listOf("Shuffle", "Previous", "Next", "Repeat")) compose.onNodeWithContentDescription(control).assertDoesNotExist()
        compose.waitUntil(5_000) { player.repeatMode == Player.REPEAT_MODE_OFF } // or it would loop with no way out
        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitUntil(5_000) { closed }
    }

    @Test fun backInTheAppTheMiniPlayerShowsTheFile() {
        compose.setContent {
            MakoApp(hasPermission = true, library = library, player = player, hidden = Hidden(context), status = null, onGrant = {}, onRescan = {})
        }
        main { player.open(file) }
        waitFor("Voice note") // the mini player
        compose.onNodeWithText("Face Value").assertExists() // over the library
        compose.onNodeWithText("Voice note").performClick()
        waitFor("PLAYING A FILE")
        compose.onNodeWithContentDescription("Up next").assertExists()
    }
}
