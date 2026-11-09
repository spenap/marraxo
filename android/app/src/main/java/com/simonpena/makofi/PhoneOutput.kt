package com.simonpena.makofi

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** An audio output as Android lists it: an AudioDeviceInfo type and product name. */
data class OutputDevice(val type: Int, val name: String)

/** What "This phone" plays through. */
sealed interface PhoneOutput {
    data object Speaker : PhoneOutput
    data object Headphones : PhoneOutput
    /** [name]: the device's own ("Galaxy Buds3"), empty if it has none. */
    data class Bluetooth(val name: String) : PhoneOutput
}

/** The output Android routes music to: Bluetooth, then wired or USB headphones, then the speaker. */
fun phoneOutput(devices: List<OutputDevice>): PhoneOutput {
    devices.firstOrNull { it.type in BLUETOOTH }?.let { return PhoneOutput.Bluetooth(it.name.trim()) }
    if (devices.any { it.type in HEADPHONES }) return PhoneOutput.Headphones
    return PhoneOutput.Speaker
}

// AudioDeviceInfo types: A2DP, hearing aid, LE Audio headset and speaker; SCO is for calls.
private val BLUETOOTH = setOf(8, 23, 26, 27)
// Wired headset, wired headphones, USB headset.
private val HEADPHONES = setOf(3, 4, 22)

/** [phoneOutput] now, updated as devices connect and disconnect. */
@Composable
fun rememberPhoneOutput(): PhoneOutput {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    fun read() = phoneOutput(
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { OutputDevice(it.type, it.productName?.toString().orEmpty()) },
    )
    var output by remember { mutableStateOf(read()) }
    DisposableEffect(audio) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { output = read() }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { output = read() }
        }
        audio.registerAudioDeviceCallback(callback, null)
        onDispose { audio.unregisterAudioDeviceCallback(callback) }
    }
    return output
}
