package com.simonpena.makofi

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The library when Makofi is (or isn't) the phone's home screen. */
@RunWith(AndroidJUnit4::class)
class HomeUiTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var player: PlayerHolder
    private val homePresses = mutableIntStateOf(0)
    private val library = listOf(
        Album(1, "The Blue Idol", "Altan", 2002, listOf(
            Track(100, "Daily Growing", "Altan", "The Blue Idol", 1, 200_000, 1, Uri.parse("content://media/external/audio/media/100")),
        ), "Celtic"),
    )

    private fun show(isHome: Boolean) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { player = PlayerHolder(context) }
        compose.setContent {
            MakoApp(
                hasPermission = true, library = library, player = player, hidden = Hidden(context), status = null,
                onGrant = {}, onRescan = {}, isHome = isHome, homePresses = homePresses.intValue,
            )
        }
    }

    @After fun tearDown() = InstrumentationRegistry.getInstrumentation().runOnMainSync { player.release() }

    @Test fun asAnAppTheMenuHasNoWayOut() {
        show(isHome = false)
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Android settings").assertDoesNotExist()
        compose.onNodeWithText("Other apps").assertDoesNotExist()
    }

    @Test fun asTheHomeScreenTheMenuLeadsToSettingsAndOtherApps() {
        show(isHome = true)
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Android settings").assertExists()
        compose.onNodeWithText("Other apps").assertExists()
    }

    @Test fun theMenuOffersToImportLyricsAndNotes() {
        show(isHome = false)
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Import lyrics and notes").assertExists()
    }

    @Test fun pressingHomeReturnsToTheLibrary() {
        show(isHome = true)
        compose.onNodeWithText("The Blue Idol").performClick()
        compose.onNodeWithText("Daily Growing").assertExists()
        homePresses.intValue++
        compose.onNodeWithText("Albums").assertExists() // the library's tabs
        compose.onNodeWithText("Daily Growing").assertDoesNotExist()
    }
}
