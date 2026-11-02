package com.simonpena.makofi

import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.runner.AndroidJUnitRunner

/**
 * Keeps the phone awake and unlocked for the whole run. The Nexus 4 locks
 * after ~30 s, and Makofi is its home screen: once it sleeps, waking shows
 * Makofi's Now Playing over the lock screen, and every test after that finds
 * no window ("No compose hierarchies found"). The screen setting is put back
 * as it was at the end.
 */
class AwakeRunner : AndroidJUnitRunner() {
    private var stayOn = "0"

    override fun onStart() {
        stayOn = shell("settings get global stay_on_while_plugged_in").trim().ifEmpty { "0" }
        shell("settings put global stay_on_while_plugged_in 7") // AC, USB and wireless
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        super.onStart()
    }

    override fun finish(resultCode: Int, results: Bundle?) {
        shell("settings put global stay_on_while_plugged_in $stayOn")
        super.finish(resultCode, results)
    }

    /** Runs [command] as the shell user and waits for it. */
    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command)).use { it.readBytes().decodeToString() }
}
