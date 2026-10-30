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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Years tab's scrubber and its decade/year label. */
@RunWith(AndroidJUnit4::class)
class YearsUiTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var player: PlayerHolder

    // Two albums a year from 1960 to 2019, plus one with no year: far more than a screen.
    private val library = (1960..2019).flatMap { y -> listOf(y * 10L, y * 10L + 1) }.map { id ->
        val year = (id / 10).toInt()
        Album(id, "Album $id", "Artist $year", year, listOf(
            Track(id * 100, "Song $id", "Artist $year", "Album $id", id, 200_000, 1, Uri.parse("content://media/external/audio/media/${id * 100}")),
        ))
    } + Album(1, "Demo", "An Danzza", 0, listOf(
        Track(100, "Exiled Druid", "An Danzza", "Demo", 1, 200_000, 1, Uri.parse("content://media/external/audio/media/100")),
    ))

    @Before fun setUp() {
        context.getSharedPreferences("hidden", Context.MODE_PRIVATE).edit().clear().commit()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { player = PlayerHolder(context) }
        compose.setContent {
            MakoApp(hasPermission = true, library = library, player = player, hidden = Hidden(context), status = null, onGrant = {}, onRescan = {})
        }
        compose.onNodeWithText("Years").performClick()
    }

    @After fun tearDown() = InstrumentationRegistry.getInstrumentation().runOnMainSync { player.release() }

    @Test fun theScrubberMarksDecades() {
        compose.onNodeWithText("’70").assertExists()
        compose.onNodeWithText("’10").assertExists()
    }

    @Test fun draggingToTheBottomReachesAlbumsWithNoYear() {
        compose.onNodeWithText("Unknown year").assertDoesNotExist() // far down the list
        compose.onNodeWithTag("yearScrubber").performTouchInput {
            down(topCenter)
            moveTo(center)
            moveTo(bottomCenter)
            up()
        }
        compose.onNodeWithText("Unknown year").assertExists()
    }

    @Test fun whileDraggingTheLabelShowsTheDecade() {
        compose.onNodeWithTag("yearScrubber").performTouchInput {
            down(topCenter)
            moveTo(center)
        }
        compose.onNodeWithTag("yearLabel").assertTextEquals("1980s") // the middle of 1960–2019
        compose.onNodeWithTag("yearScrubber").performTouchInput { up() }
    }
}
