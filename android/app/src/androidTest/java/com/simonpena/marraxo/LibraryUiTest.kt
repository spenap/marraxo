package com.simonpena.marraxo

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
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
            Track(id * 100 + i, s, artist, title, id, 200_000, i + 1, silence)
        }, genre)

    private val library = listOf(
        album(1, "The Blue Idol", "Altan", 2002, "Celtic", "Daily Growing", "Uncle Rat"),
        album(2, "Local Ground", "Altan", 2005, "Celtic", "Dúlamán"),
        // Two discs, a song each.
        album(3, "A Son de Guerra", "Juan Luis Guerra", 2010, "Merengue", "Apaga y vámonos", "Bachata en Fukuoka").let { a ->
            a.copy(tracks = a.tracks.mapIndexed { i, t -> t.copy(number = 1, disc = i + 1) })
        },
        album(4, "Demo", "An Danzza", 0, "", "Exiled Druid"),
        Album(5, "Anthems: Trance", "Various Artists", 2013, genre = "Trance", tracks = listOf("Insomnia" to "Faithless", "Sandstorm" to "Darude").mapIndexed { i, (s, a) ->
            Track(500L + i, s, a, "Anthems: Trance", 5, 200_000, i + 1, silence)
        }),
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

    @Test fun anAlbumHasTheSameSheetWithUndo() {
        compose.onNodeWithText("Local Ground").performTouchInput { longClick() }
        compose.onNodeWithText("Altan · 2005").assertExists() // the sheet says which album
        compose.onNodeWithText("Hide album").performClick()
        compose.onNodeWithText("Local Ground").assertDoesNotExist()
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithText("Local Ground").assertExists()
    }

    @Test fun theAlbumScreenHasAMenuToo() {
        compose.onNodeWithText("A Son de Guerra").performClick()
        compose.onNodeWithContentDescription("Album options").performClick()
        compose.onNodeWithText("Go to artist").performClick()
        compose.onNodeWithText("1 album").assertExists()
    }

    @Test fun aCompilationSaysWhoPlaysEachSong() {
        compose.onNodeWithText("Anthems: Trance").performClick()
        compose.onNodeWithText("Faithless").assertExists()
        compose.onNodeWithText("Darude").assertExists()
    }

    @Test fun anAlbumByOneArtistDoesntRepeatItOnEverySong() {
        compose.onNodeWithText("The Blue Idol").performClick()
        compose.onNodeWithText("Uncle Rat").assertExists()
        compose.onAllNodesWithText("Altan").assertCountEquals(1) // the header only
    }

    @Test fun aDoubleAlbumShowsWhereEachDiscStarts() {
        compose.onNodeWithText("A Son de Guerra").performClick()
        compose.onNodeWithContentDescription("Disc 1").assertExists()
        compose.onNodeWithContentDescription("Disc 2").assertExists()
    }

    @Test fun aSingleDiscAlbumHasNoDiscRows() {
        compose.onNodeWithText("The Blue Idol").performClick()
        compose.onNodeWithText("Uncle Rat").assertExists()
        compose.onNodeWithContentDescription("Disc 1").assertDoesNotExist()
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
        compose.onNodeWithText("Start radio").performClick() // one radio, no modes to choose
        compose.waitUntil(5_000) { player.currentTrackId == 200L }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Dúlamán").performClick() // the mini player
        compose.onNodeWithText("Radio from Dúlamán").assertExists()
    }

    @Test fun everySongHasAVisibleMenuWithItsActions() {
        compose.onNodeWithText("A Son de Guerra").performClick()
        compose.onAllNodesWithContentDescription("Song options")[1].performClick() // Bachata en Fukuoka
        compose.onNodeWithText("Juan Luis Guerra · A Son de Guerra").assertExists() // the sheet says which song
        compose.onNodeWithText("Play next").assertExists()
        compose.onNodeWithText("Start radio").assertExists()
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

    @Test fun shuffleSaysWhetherItsOnNotOnlyByColour() {
        nowPlayingGuerra()
        compose.onNodeWithContentDescription("Shuffle").assertIsOff()
        compose.onNodeWithContentDescription("Shuffle").performClick()
        compose.waitUntil(5_000) { player.shuffle }
        compose.onNodeWithContentDescription("Shuffle").assertIsOn()
        compose.onNodeWithContentDescription("Shuffle").performClick() // off again: the queue store keeps it
        compose.waitUntil(5_000) { !player.shuffle }
    }

    @Test fun thePlayingSongIsMarkedNotOnlyByColour() {
        compose.onNodeWithText("A Son de Guerra").performClick()
        compose.onNodeWithText("Play").performClick()
        compose.waitUntil(5_000) { player.currentTrackId == 300L }
        compose.onNodeWithContentDescription("Now playing").assertExists()
    }

    @Test fun anArtistCanBePlayedOrShuffledWhole() {
        compose.onNodeWithText("Artists").performClick()
        compose.onNodeWithText("Altan").performClick()
        compose.onNodeWithContentDescription("Shuffle").performClick()
        compose.waitUntil(5_000) { player.currentTrackId in setOf(100L, 101L, 200L) } // Altan's three songs
        compose.waitUntil(5_000) { player.queue.size == 3 }
    }

    @Test fun anAlbumCanBeShuffled() {
        compose.onNodeWithText("The Blue Idol").performClick()
        compose.onNodeWithContentDescription("Shuffle").performClick()
        compose.waitUntil(5_000) { player.queue.size == 2 && player.currentTrackId in setOf(100L, 101L) }
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
