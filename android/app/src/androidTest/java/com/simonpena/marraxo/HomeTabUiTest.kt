package com.simonpena.marraxo

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
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

/** The Home tab: picking up the last album, and more like it. */
@RunWith(AndroidJUnit4::class)
class HomeTabUiTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var player: PlayerHolder

    private fun album(id: Long, title: String, artist: String, year: Int, genre: String, vararg songs: String) =
        Album(id, title, artist, year, songs.mapIndexed { i, s -> Track(id * 100 + i, s, artist, title, id, 200_000, i + 1, silence) }, genre)

    private val blueIdol = album(1, "The Blue Idol", "Altan", 2002, "Celtic", "Daily Growing", "Uncle Rat", "Roisin")
    private val library = listOf(
        blueIdol,
        album(2, "Local Ground", "Altan", 2005, "Celtic", "Dúlamán"),
        album(3, "Fused", "Michael McGoldrick", 2000, "Celtic", "Fused"),
        album(4, "Red Crow", "Rura", 2001, "Celtic", "Red Crow"),
        album(5, "Year of the Dog", "Wolfstone", 2009, "Celtic", "Holy Ground"),
        album(6, "Is This It", "The Strokes", 2001, "Indie Rock", "Is This It", "The Modern Age"),
    )

    private fun start(plays: List<Play>) {
        compose.setContent {
            MakoApp(hasPermission = true, library = library, player = player, hidden = Hidden(context), status = null, onGrant = {}, onRescan = {}, plays = plays)
        }
    }

    @Before fun setUp() {
        context.getSharedPreferences("hidden", Context.MODE_PRIVATE).edit().clear().commit()
        main { player = PlayerHolder(context) }
    }

    @After fun tearDown() {
        main { player.play(emptyList(), 0) }
        compose.waitUntil(5_000) { player.currentTrackId == null }
        main { player.release() }
    }

    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Test fun withPlaysTheAppOpensOnHomeAndPicksUpTheAlbumAtItsSong() {
        start(listOf(Play("Uncle Rat", "Altan", "The Blue Idol", 1_000, "completed")))
        compose.onNodeWithText("Pick up where you left off", ignoreCase = true).assertExists()
        compose.onNodeWithContentDescription("Play The Blue Idol from Roisin").performClick()
        compose.waitUntil(5_000) { player.currentTrackId == blueIdol.tracks[2].id }
    }

    @Test fun homeShowsMoreOfTheGenreAndDecadeOfWhatWasPlayed() {
        start(listOf(Play("Uncle Rat", "Altan", "The Blue Idol", 1_000, "completed")))
        compose.onNodeWithText("Celtic · 2000s").assertExists()
        compose.onNodeWithText("Like The Blue Idol").assertExists()
        compose.onNodeWithText("Fused").performClick()
        compose.onNodeWithText("Michael McGoldrick").assertExists() // the album opened
    }

    @Test fun withoutPlaysTheAppOpensOnAlbums() {
        start(emptyList())
        compose.onNodeWithText("Is This It").assertExists()
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("Albums you play show up here").assertExists()
    }
}
