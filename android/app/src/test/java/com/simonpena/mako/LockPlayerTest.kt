package com.simonpena.mako

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LockPlayerTest {
    @Test fun `shows on the Nexus 4 as the home screen, with music loaded`() {
        assertTrue(shouldShowLockPlayer(sdk = 27, isHome = true, hasMusic = true))
    }

    @Test fun `not when nothing is loaded`() {
        assertFalse(shouldShowLockPlayer(sdk = 27, isHome = true, hasMusic = false))
    }

    @Test fun `not as an ordinary app`() {
        assertFalse(shouldShowLockPlayer(sdk = 27, isHome = false, hasMusic = true))
    }

    @Test fun `not on Android 10 and later, whose lock screen is fine`() {
        assertFalse(shouldShowLockPlayer(sdk = 29, isHome = true, hasMusic = true))
    }

    @Test fun `the session's item gives back the track`() {
        val t = track(id = 7, albumId = 3, title = "Me voy pa Sinabicu", durationMs = 201_000)
        assertEquals(t.copy(number = 0), t.toMediaItem().toTrack())
    }

    @Test fun `showing it starts the lock-screen player in its own task`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        showLockPlayer(app)
        val started = shadowOf(app).nextStartedActivity
        assertEquals(LockActivity::class.java.name, started.component?.className)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
