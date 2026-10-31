package com.simonpena.mako

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

/** Letter scrubbers on the Albums and Artists tabs; short lists don't get one. */
@RunWith(AndroidJUnit4::class)
class LettersUiTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var player: PlayerHolder

    // Six artists per letter, no Q, one album each and all one genre: long
    // Albums and Artists lists, a one-row Genres list.
    private val library = ('A'..'Z').filter { it != 'Q' }.flatMap { c -> (1..6).map { n -> "$c${"x".repeat(n)} Band" } }
        .mapIndexed { i, artist ->
            val id = i + 1L
            Album(id, "Album $id", artist, 2000, listOf(
                Track(id * 100, "Song $id", artist, "Album $id", id, 200_000, 1, Uri.parse("content://media/external/audio/media/${id * 100}")),
            ), genre = "Rock")
        }

    @Before fun setUp() {
        context.getSharedPreferences("hidden", Context.MODE_PRIVATE).edit().clear().commit()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { player = PlayerHolder(context) }
        compose.setContent {
            MakoApp(hasPermission = true, library = library, player = player, hidden = Hidden(context), status = null, onGrant = {}, onRescan = {})
        }
    }

    @After fun tearDown() = InstrumentationRegistry.getInstrumentation().runOnMainSync { player.release() }

    @Test fun artistsHaveLetterMarks() {
        compose.onNodeWithText("Artists").performClick()
        compose.onNodeWithTag("scrubber").assertExists()
        compose.onNodeWithText("Q").assertDoesNotExist()
    }

    @Test fun draggingToTheBottomOfArtistsReachesZ() {
        compose.onNodeWithText("Artists").performClick()
        compose.onNodeWithText("Zxxxxxx Band").assertDoesNotExist()
        compose.onNodeWithTag("scrubber").performTouchInput { down(topCenter); moveTo(center); moveTo(bottomCenter); up() }
        compose.onNodeWithText("Zxxxxxx Band").assertExists()
    }

    @Test fun whileDraggingTheAlbumsLabelShowsTheLetter() {
        compose.onNodeWithTag("scrubber").performTouchInput { down(topCenter); moveTo(center) }
        compose.onNodeWithTag("scrubLabel").assertTextEquals("M") // the middle of A–Z without Q
        compose.onNodeWithTag("scrubber").performTouchInput { up() }
    }

    @Test fun aShortListHasNoScrubber() {
        compose.onNodeWithText("Genres").performClick()
        compose.onNodeWithTag("scrubber").assertDoesNotExist()
    }
}
