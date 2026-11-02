package com.simonpena.makofi

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Lists or tiles for Genres and Artists: tried for a while in the beta, so it's a remembered switch. */
@RunWith(RobolectricTestRunner::class)
class LookTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `tiles to start with`() {
        assertTrue(Look(context).tiles)
    }

    @Test fun `the choice survives a restart`() {
        Look(context).tiles = false
        assertFalse(Look(context).tiles)
        Look(context).tiles = true
        assertTrue(Look(context).tiles)
    }
}
