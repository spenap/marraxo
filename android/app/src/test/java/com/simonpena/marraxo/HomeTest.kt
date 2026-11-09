package com.simonpena.marraxo

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Marraxo as the Nexus 4's home screen (roadmap A6), off everywhere else. */
@RunWith(RobolectricTestRunner::class)
class HomeTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    private fun homeActivities() = context.packageManager.queryIntentActivities(homeIntent, 0)
        .map { it.activityInfo.name }

    @Test fun `installing the app never makes it a home screen`() {
        assertFalse(HOME_ALIAS in homeActivities())
        assertFalse(isHomeApp(context))
    }

    @Test fun `enabling the home entry offers Marraxo as a home screen`() {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context.packageName, HOME_ALIAS),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP,
        )
        assertTrue(HOME_ALIAS in homeActivities())
    }

    @Test fun `other apps opens a launcher that is not Marraxo`() {
        val trebuchet = ComponentName("org.lineageos.trebuchet", "com.android.launcher3.searchlauncher.SearchLauncher")
        val candidates = listOf(ComponentName("com.simonpena.marraxo", HOME_ALIAS), trebuchet)
        assertEquals(trebuchet, otherLauncher(candidates, "com.simonpena.marraxo"))
    }

    @Test fun `no other launcher means nothing to open`() {
        assertNull(otherLauncher(listOf(ComponentName("com.simonpena.marraxo", HOME_ALIAS)), "com.simonpena.marraxo"))
    }
}
