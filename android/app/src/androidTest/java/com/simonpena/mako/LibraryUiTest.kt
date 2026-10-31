package com.simonpena.mako

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The library screens as a person uses them: tabs, filters, hiding and
 * search. Runs on a phone or an emulator: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class LibraryUiTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var player: PlayerHolder
    private lateinit var hidden: Hidden

    private fun album(id: Long, title: String, artist: String, year: Int, genre: String, vararg songs: String) =
        Album(id, title, artist, year, songs.mapIndexed { i, s ->
            Track(id * 100 + i, s, artist, title, id, 200_000, i + 1, Uri.parse("content://media/external/audio/media/${id * 100 + i}"))
        }, genre)

    private val library = listOf(
        album(1, "The Blue Idol", "Altan", 2002, "Celtic", "Daily Growing", "Uncle Rat"),
        album(2, "Local Ground", "Altan", 2005, "Celtic", "Dúlamán"),
        album(3, "A Son de Guerra", "Juan Luis Guerra", 2010, "Merengue", "Apaga y vámonos", "Bachata en Fukuoka"),
        album(4, "Demo", "An Danzza", 0, "", "Exiled Druid"),
    )

    @Before fun setUp() {
        context.getSharedPreferences("hidden", Context.MODE_PRIVATE).edit().clear().commit()
        // MediaController lives on the main thread; tests run on another one.
        main { player = PlayerHolder(context) }
        hidden = Hidden(context)
        compose.setContent {
            MakoApp(hasPermission = true, library = library, player = player, hidden = hidden, status = null, onGrant = {}, onRescan = {})
        }
    }

    /** Each test starts with nothing playing: the mini player would otherwise show a song from the one before. */
    @After fun tearDown() {
        main { player.play(emptyList(), 0) }
        compose.waitUntil(5_000) { player.currentTrackId == null }
        main { player.release() }
    }

    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Test fun anArtistOpensALevelDownAndBackReturnsToArtists() {
        compose.onNodeWithText("Artists").performClick()
        compose.onNodeWithText("Juan Luis Guerra").performClick()
        compose.onNodeWithText("1 album").assertExists()
        compose.onNodeWithText("A Son de Guerra").assertExists()
        compose.onNodeWithText("The Blue Idol").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Juan Luis Guerra").assertExists() // the artists, not every album
        compose.onNodeWithText("A Son de Guerra").assertDoesNotExist()
    }

    @Test fun genreTabFilters() {
        compose.onNodeWithText("Genres").performClick()
        compose.onNodeWithText("No genre").assertExists() // the demo has none
        compose.onNodeWithText("Celtic").performClick()
        compose.onNodeWithText("2 albums").assertExists()
        compose.onNodeWithText("A Son de Guerra").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("No genre").assertExists() // back on Genres
    }

    @Test fun yearsTabGroupsAlbums() {
        compose.onNodeWithText("Years").performClick()
        compose.onNodeWithText("2010").assertExists()
        compose.onNodeWithText("Unknown year").assertExists()
    }

    @Test fun aHiddenAlbumCanBeShownAgain() {
        compose.onNodeWithText("Local Ground").performTouchInput { longClick() }
        compose.onNodeWithText("Hide album").performClick()
        compose.onNodeWithText("Local Ground").assertDoesNotExist()

        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Hidden").performClick()
        compose.onNodeWithText("Local Ground").assertExists()
        compose.onNodeWithText("Show").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Local Ground").assertExists()
    }

    @Test fun aHiddenSongLeavesTheRestOfTheAlbum() {
        compose.onNodeWithText("The Blue Idol").performClick()
        compose.onNodeWithText("Uncle Rat").performTouchInput { longClick() }
        compose.onNodeWithText("Hide song").performClick()
        compose.onNodeWithText("Uncle Rat").assertDoesNotExist()
        compose.onNodeWithText("Daily Growing").assertExists()
    }

    @Test fun aSongStartsARadioAndNowPlayingSaysSo() {
        compose.onNodeWithText("Local Ground").performClick()
        compose.onNodeWithText("Dúlamán").performTouchInput { longClick() }
        compose.onNodeWithText("Start radio").performClick()
        compose.onNodeWithText("Same era").performClick()
        compose.waitUntil(5_000) { player.currentTrackId == 200L }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Dúlamán").performClick() // the mini player
        compose.onNodeWithText("Radio · Same era").assertExists()
    }

    @Test fun everySongHasAVisibleMenuWithItsActions() {
        compose.onNodeWithText("A Son de Guerra").performClick()
        compose.onAllNodesWithContentDescription("Song options")[1].performClick() // Bachata en Fukuoka
        compose.onNodeWithText("Juan Luis Guerra · A Son de Guerra").assertExists() // the sheet says which song
        compose.onNodeWithText("Play next").assertExists()
        compose.onNodeWithText("Start radio").performClick()
        compose.onNodeWithText("Anything goes").assertExists() // the modes open in place
        compose.onNodeWithText("Go to artist").performClick()
        compose.onNodeWithText("1 album").assertExists() // Juan Luis Guerra's level
    }

    @Test fun aHiddenSongCanBeUndoneRightAway() {
        compose.onNodeWithText("The Blue Idol").performClick()
        compose.onAllNodesWithContentDescription("Song options")[1].performClick() // Uncle Rat
        compose.onNodeWithText("Hide song").performClick()
        compose.onNodeWithText("Uncle Rat").assertDoesNotExist()
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithText("Uncle Rat").assertExists()
    }

    /** Plays A Son de Guerra and opens Now Playing from the mini player. */
    private fun nowPlayingGuerra() {
        compose.onNodeWithText("A Son de Guerra").performClick()
        compose.onNodeWithText("Play").performClick()
        compose.waitUntil(5_000) { player.currentTrackId == 300L }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Apaga y vámonos").performClick() // the mini player
    }

    @Test fun upNextShowsTheQueueAndJumpsWithinIt() {
        nowPlayingGuerra()
        compose.onNodeWithContentDescription("Up next").performClick()
        compose.onNodeWithText("Bachata en Fukuoka").performClick()
        compose.waitUntil(5_000) { player.currentTrackId == 301L }
    }

    @Test fun nowPlayingHasTheSongMenuToo() {
        nowPlayingGuerra()
        compose.onNodeWithContentDescription("Song options").performClick()
        compose.onNodeWithText("Go to artist").performClick()
        compose.onNodeWithText("1 album").assertExists()
    }

    @Test fun nowPlayingLeadsToItsAlbumAndArtist() {
        nowPlayingGuerra()
        compose.onNodeWithText("Juan Luis Guerra").performClick()
        compose.onNodeWithText("1 album").assertExists() // the artist's level
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("A Son de Guerra").performClick() // the album, at the top of Now Playing
        compose.onNodeWithText("Play").assertExists() // the album screen
    }

    @Test fun searchIgnoresAccentsAndPlaysTheSong() {
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("vamonos")
        compose.onNodeWithText("Apaga y vámonos").performClick()
        compose.waitUntil(5_000) { player.currentTrackId == 300L }
    }
}
