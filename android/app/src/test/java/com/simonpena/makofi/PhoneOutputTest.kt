package com.simonpena.makofi

import android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
import android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
import android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
import android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
import android.media.AudioDeviceInfo.TYPE_USB_HEADSET
import android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneOutputTest {
    private val speaker = OutputDevice(TYPE_BUILTIN_SPEAKER, "SM-S931B")
    private val earpiece = OutputDevice(TYPE_BUILTIN_EARPIECE, "SM-S931B")

    @Test fun `with nothing connected it plays on the speaker`() {
        assertEquals(PhoneOutput.Speaker, phoneOutput(listOf(earpiece, speaker)))
    }

    @Test fun `wired or USB headphones`() {
        assertEquals(PhoneOutput.Headphones, phoneOutput(listOf(speaker, OutputDevice(TYPE_WIRED_HEADPHONES, ""))))
        assertEquals(PhoneOutput.Headphones, phoneOutput(listOf(speaker, OutputDevice(TYPE_USB_HEADSET, "USB-C to 3.5mm"))))
    }

    @Test fun `Bluetooth by its name, over headphones as Android routes it`() {
        val buds = OutputDevice(TYPE_BLUETOOTH_A2DP, "Galaxy Buds3")
        assertEquals(PhoneOutput.Bluetooth("Galaxy Buds3"), phoneOutput(listOf(speaker, OutputDevice(TYPE_WIRED_HEADPHONES, ""), buds)))
    }

    @Test fun `a Bluetooth device for calls only doesn't play music`() {
        assertEquals(PhoneOutput.Speaker, phoneOutput(listOf(speaker, OutputDevice(TYPE_BLUETOOTH_SCO, "Car"))))
    }

    @Test fun `a Bluetooth device without a name`() {
        assertEquals(PhoneOutput.Bluetooth(""), phoneOutput(listOf(speaker, OutputDevice(TYPE_BLUETOOTH_A2DP, " "))))
    }
}
