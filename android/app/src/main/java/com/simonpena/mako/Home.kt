package com.simonpena.mako

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Mako as the phone's home screen (roadmap A6). The manifest's home entry
 * is disabled, so installing the app never takes over a launcher; on the
 * Nexus 4, android/scripts/appliance.sh enables it and makes it the default.
 */
const val HOME_ALIAS = "com.simonpena.mako.Home" // class name, the same in the debug app

private val homeIntent get() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

/** Whether pressing Home lands in Mako. */
fun isHomeApp(context: Context): Boolean =
    context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName == context.packageName

/** The launcher behind "Other apps": any home screen that isn't Mako (Trebuchet on the Nexus 4). */
fun otherLauncher(candidates: List<ComponentName>, ownPackage: String): ComponentName? =
    candidates.firstOrNull { it.packageName != ownPackage && it.packageName != "android" }

fun openOtherLauncher(context: Context) {
    val candidates = context.packageManager.queryIntentActivities(homeIntent, 0)
        .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
    val target = otherLauncher(candidates, context.packageName) ?: return
    context.startActivity(Intent(homeIntent).setComponent(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
