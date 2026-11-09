package com.simonpena.marraxo

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Genres tab's families on a real phone: a big family opens to its
 * genres, a small one straight to its albums, and Back leaves a family.
 */
@RunWith(AndroidJUnit4::class)
class GenresUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var player: PlayerHolder

    private fun album(id: Long, genre: String) = Album(id, "Album $id", "Artist $id", 2000, listOf(
        Track(id * 100, "Song $id", "Artist $id", "Album $id", id, 200_000, 1, Uri.parse("content://media/external/audio/media/${id * 100}")),
    ), genre)

    // Rock: 14 albums in two genres (opens to the genres). Indie: 3 albums (opens to them).
    private val library = (1L..12L).map { album(it, "Rock") } + album(13, "Punk Rock") + album(14, "Punk-Rock") +
        album(15, "Indie") + album(16, "Indie Pop") + album(17, "Indie Pop") +
        album(18, "Folk") + album(19, "Flamenco") + album(20, "Rap") // one album each: Other

    @Before fun setUp() {
        context.getSharedPreferences("hidden", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("look", Context.MODE_PRIVATE).edit().clear().commit() // tiles
        main { player = PlayerHolder(context) }
        compose.setContent {
            MakoApp(hasPermission = true, library = library, player = player, hidden = Hidden(context), status = null, onGrant = {}, onRescan = {})
        }
        compose.onNodeWithText("Genres").performClick()
    }

    @After fun tearDown() = main { player.release() }

    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun back() = compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    @Test fun aBigFamilyOpensToItsGenresAndBackLeavesIt() {
        compose.onNodeWithText("2 genres").performClick() // Rock
        compose.onNodeWithText("All albums").assertExists()
        compose.onNodeWithText("Punk Rock").assertExists() // Punk-Rock is the same genre
        compose.onNodeWithText("2 albums").performClick()
        compose.onNodeWithText("Album 13").assertExists() // the grid, filtered to Punk Rock
        compose.onNodeWithText("Album 1").assertDoesNotExist()
        back()
        compose.onNodeWithText("All albums").assertExists() // still in Rock
        back()
        compose.onNodeWithText("Other").assertExists() // the top level again
    }

    @Test fun theMenuSwitchesBetweenTilesAndListsAndRemembersIt() {
        compose.onNodeWithTag("tiles").assertExists()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Show as lists").performClick()
        compose.onNodeWithTag("rows").assertExists()
        compose.onNodeWithText("Artists").performClick()
        compose.onNodeWithTag("rows").assertExists() // Artists follows too
        assertFalse(Look(context).tiles) // and it's saved
    }

    @Test fun genresWithOneAlbumAreGatheredInOther() {
        compose.onNodeWithText("Folk").assertDoesNotExist()
        compose.onNodeWithText("Other").performClick() // 3 albums: straight to them
        compose.onNodeWithText("Album 18").assertExists()
        compose.onNodeWithText("Album 20").assertExists()
    }

    @Test fun aGenreOpensAtItsFirstAlbumWhereverTheAlbumsTabWasScrolled() {
        compose.onNodeWithText("Albums").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(19) // the last album
        compose.onNodeWithText("Genres").performClick()
        compose.onNodeWithText("2 genres").performClick() // Rock: 14 albums, more than a screen
        compose.onNodeWithText("All albums").performClick()
        compose.onNodeWithText("Album 1").assertIsDisplayed() // its first, not its last
    }

    @Test fun aSmallFamilyOpensStraightToItsAlbums() {
        compose.onNodeWithText("Indie").performClick()
        compose.onNodeWithText("Album 16").assertExists()
        compose.onNodeWithText("3 albums").assertExists() // the chip's count
    }
}
