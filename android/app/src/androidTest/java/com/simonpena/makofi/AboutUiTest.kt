package com.simonpena.makofi

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** About: version, device, and the licences of the open-source code the app is built on. */
@RunWith(AndroidJUnit4::class)
class AboutUiTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var player: PlayerHolder

    @Before fun setUp() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { player = PlayerHolder(context) }
        val library = listOf(Album(1, "The Blue Idol", "Altan", 2002, listOf(
            Track(100, "Daily Growing", "Altan", "The Blue Idol", 1, 200_000, 1, Uri.parse("content://media/external/audio/media/100")),
        )))
        compose.setContent {
            MakoApp(hasPermission = true, library = library, player = player, hidden = Hidden(context), status = null, onGrant = {}, onRescan = {})
        }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("About").performClick()
    }

    @After fun tearDown() = InstrumentationRegistry.getInstrumentation().runOnMainSync { player.release() }

    @Test fun aboutNamesItsLicenceAndWhereItsDataComesFrom() {
        compose.onNodeWithText("MIT", substring = true).assertExists()
        compose.onNodeWithText("github.com/spenap/makofi", substring = true).assertExists()
        // Wikipedia's summaries are CC BY-SA: shipping them means crediting them.
        compose.onNodeWithText("Wikipedia", substring = true).assertExists()
        compose.onNodeWithText("MusicBrainz", substring = true).assertExists()
    }

    @Test fun aboutLeadsToTheLicences() {
        compose.onNodeWithText("Open-source licences").performClick()
        compose.onNodeWithText("Licences").assertExists()
        // Media3 plays the music; its licence must be credited.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Media3 ExoPlayer", substring = true)) // further down the list
        compose.onNode(hasText("Media3 ExoPlayer", substring = true)).assertExists()
        assertTrue(compose.onAllNodesWithText("Apache License 2.0", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun aLicenceOpensToItsText() {
        compose.onNodeWithText("Open-source licences").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Media3 ExoPlayer", substring = true))
        compose.onNode(hasText("Media3 ExoPlayer", substring = true)).performClick()
        compose.onNode(hasText("Licensed under the Apache License", substring = true)).assertExists()
    }
}
