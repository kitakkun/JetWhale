package com.kitakkun.jetwhale.plugins.mirror.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeviceControllerPartsTest {
    @Test
    fun `an Android device has every part and all six buttons`() {
        val android = AndroidDeviceController(adbPath = "adb", serial = "emulator-5554", emulatorScreens = null, ffmpegPath = null)

        assertEquals(DeviceButton.entries.toSet(), assertNotNull(android.input).buttons.toSet())
        assertNotNull(android.power)
        assertNotNull(android.recorder)
    }

    @Test
    fun `a simulator with idb takes input with its three buttons and records but has no screen power`() {
        val simulator = IosSimulatorDeviceController(udid = "sim", xcrunPath = "xcrun", idbPath = "idb")

        assertEquals(listOf(DeviceButton.Home, DeviceButton.Recents, DeviceButton.Power), assertNotNull(simulator.input).buttons)
        assertNotNull(simulator.recorder)
        assertNull(simulator.power)
    }

    @Test
    fun `a simulator without idb records through simctl but takes no input`() {
        val simulator = IosSimulatorDeviceController(udid = "sim", xcrunPath = "xcrun", idbPath = null)

        assertNull(simulator.input)
        assertNotNull(simulator.recorder)
        assertEquals(DeviceCapabilities(input = false, buttons = emptyList(), recording = true, screenPower = false), simulator.capabilities)
    }

    @Test
    fun `a device without input is refused with the reason its kind has one`() {
        val simulator = IosSimulatorDeviceController(udid = "sim", xcrunPath = "xcrun", idbPath = null)
        fun deviceOf(kind: DeviceKind) = MirrorDevice(DeviceListing("id", "name", kind, osVersion = null), simulator)

        assertEquals(VIEW_ONLY, deviceOf(DeviceKind.IosDevice).noInputReason)
        assertEquals(IDB_MISSING, deviceOf(DeviceKind.IosSimulator).noInputReason)
    }
}
