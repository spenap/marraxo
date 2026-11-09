package com.simonpena.marraxo

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Marraxo as the phone's home screen (roadmap A6). The manifest's home entry
 * is disabled, so installing the app never takes over a launcher; on the
 * Nexus 4, android/scripts/appliance.sh enables it and makes it the default.
 */
const val HOME_ALIAS = "com.simonpena.marraxo.Home" // class name, the same in the debug app

private val homeIntent get() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

/** Whether pressing Home lands in Marraxo. */
fun isHomeApp(context: Context): Boolean =
    context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName == context.packageName

/** The launcher behind "Other apps": any home screen that isn't Marraxo (Trebuchet on the Nexus 4). */
fun otherLauncher(candidates: List<ComponentName>, ownPackage: String): ComponentName? =
    candidates.firstOrNull { it.packageName != ownPackage && it.packageName != "android" }

fun openOtherLauncher(context: Context) {
    val candidates = context.packageManager.queryIntentActivities(homeIntent, 0)
        .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
    val target = otherLauncher(candidates, context.packageName) ?: return
    context.startActivity(Intent(homeIntent).setComponent(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * As the home screen, Marraxo hides the navigation bar (roadmap A6): Home
 * already lands here and Back is Marraxo's own. A swipe up from the bottom edge
 * shows it for a moment, which is also the way to Recents. The status bar stays.
 */
fun hideNavigationBar(activity: Activity, isHome: Boolean) {
    val decor = activity.window.decorView
    val bars = WindowCompat.getInsetsController(activity.window, decor)
    // Before Android 11, "stable" insets keep reserving a hidden bar's space,
    // which left the mini player floating 48 dp above the bottom.
    @Suppress("DEPRECATION")
    val stable = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    if (isHome) {
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.navigationBars())
        @Suppress("DEPRECATION")
        decor.systemUiVisibility = decor.systemUiVisibility and stable.inv()
    } else {
        bars.show(WindowInsetsCompat.Type.navigationBars())
        @Suppress("DEPRECATION")
        decor.systemUiVisibility = decor.systemUiVisibility or stable
    }
}
