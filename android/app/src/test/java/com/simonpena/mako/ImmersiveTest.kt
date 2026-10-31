package com.simonpena.mako

import android.view.View
import androidx.activity.ComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** The navigation bar is hidden in Mako as the home screen, and a swipe brings it back. */
@RunWith(RobolectricTestRunner::class)
class ImmersiveTest {
    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    private val flags get() = activity.window.decorView.systemUiVisibility

    @Test fun `as the home screen the navigation bar is hidden, one swipe away`() {
        hideNavigationBar(activity, isHome = true)
        assertTrue(flags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0)
        assertTrue("a swipe shows it for a moment", flags and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY != 0)
    }

    @Test fun `the bar's space goes to Mako, so the mini player sits at the bottom`() {
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE // as edge-to-edge sets it
        hideNavigationBar(activity, isHome = true)
        assertEquals("stable insets would keep reserving the hidden bar", 0, flags and View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        hideNavigationBar(activity, isHome = false)
        assertTrue(flags and View.SYSTEM_UI_FLAG_LAYOUT_STABLE != 0)
    }

    @Test fun `the status bar stays`() {
        hideNavigationBar(activity, isHome = true)
        assertEquals(0, flags and View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    @Test fun `as an ordinary app the bar is shown, and comes back when Mako stops being home`() {
        hideNavigationBar(activity, isHome = true)
        hideNavigationBar(activity, isHome = false)
        assertEquals(0, flags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }
}
