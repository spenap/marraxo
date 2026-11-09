package com.simonpena.marraxo

import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.runner.AndroidJUnitRunner

/**
 * Keeps the phone awake and unlocked for the whole run. The Nexus 4 locks
 * after ~30 s, and Marraxo is its home screen: once it sleeps, waking shows
 * Marraxo's Now Playing over the lock screen, and every test after that finds
 * no window ("No compose hierarchies found"). The tests also read English
 * labels ("More"), so on a phone set to another language the app runs in
 * English (per-app language, Android 13+). Both are put back at the end.
 * A PIN or pattern lock can't be dismissed: unlock the phone first.
 */
class AwakeRunner : AndroidJUnitRunner() {
    private var stayOn = "0"
    private val perAppLanguage = Build.VERSION.SDK_INT >= 33

    override fun onStart() {
        stayOn = shell("settings get global stay_on_while_plugged_in").trim().ifEmpty { "0" }
        shell("settings put global stay_on_while_plugged_in 7") // AC, USB and wireless
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        if (perAppLanguage) shell("cmd locale set-app-locales ${targetContext.packageName} --locales en-US")
        super.onStart()
    }

    override fun finish(resultCode: Int, results: Bundle?) {
        shell("settings put global stay_on_while_plugged_in $stayOn")
        if (perAppLanguage) shell("cmd locale set-app-locales ${targetContext.packageName} --locales \"\"")
        super.finish(resultCode, results)
    }

    /** Runs [command] as the shell user and waits for it. */
    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command)).use { it.readBytes().decodeToString() }
}
