package com.simonpena.makofi

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import java.util.Locale

/**
 * Makofi in Galician on a real phone: the translations are in the APK and
 * the screens use them. The other UI tests run in the phone's language
 * (English on the Nexus 4).
 */
@RunWith(AndroidJUnit4::class)
class LanguageUiTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var player: PlayerHolder

    private val library = listOf(
        Album(1, "The Blue Idol", "Altan", 2002, listOf(
            Track(100, "Daily Growing", "Altan", "The Blue Idol", 1, 200_000, 1, Uri.parse("content://media/external/audio/media/100")),
            Track(101, "Uncle Rat", "Altan", "The Blue Idol", 1, 200_000, 2, Uri.parse("content://media/external/audio/media/101")),
        ), ""),
    )

    @Before fun setUp() {
        context.getSharedPreferences("hidden", Context.MODE_PRIVATE).edit().clear().commit()
        main { player = PlayerHolder(context) }
        val galician = Configuration(context.resources.configuration).apply { setLocale(Locale("gl")) }
        compose.setContent {
            // The file picker finds its Activity through LocalContext, which this test replaces.
            val registry = LocalActivityResultRegistryOwner.current
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides registry!!,
                LocalContext provides context.createConfigurationContext(galician),
                LocalConfiguration provides galician,
            ) {
                MakoApp(hasPermission = true, library = library, player = player, hidden = Hidden(context), status = null, onGrant = {}, onRescan = {})
            }
        }
    }

    @After fun tearDown() = main { player.release() }

    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Test fun theLibraryIsInGalician() {
        compose.onNodeWithText("Biblioteca").assertExists()
        compose.onNodeWithText("Álbums").assertExists()
        compose.onNodeWithText("Xéneros").performClick()
        compose.onNodeWithText("Sen xénero").assertExists()
        compose.onNodeWithText("1 álbum").assertExists()
        compose.onNodeWithContentDescription("Máis opcións").assertExists()
        // (Not the ⋮ menu itself: a popup is its own window, with the phone's
        // language rather than the one this test injects.)
    }
}
