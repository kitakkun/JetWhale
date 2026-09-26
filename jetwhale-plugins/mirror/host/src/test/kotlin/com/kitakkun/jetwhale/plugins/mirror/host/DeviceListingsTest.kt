package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DeviceListingsTest {
    @Test
    fun `adb lists ready emulators and devices and leaves out the ones it cannot use`() {
        val output = """
            List of devices attached
            emulator-5554          device product:sdk_gphone64_arm64 model:sdk_gphone64_arm64 device:emu64a transport_id:1
            R5CT1234ABC            device usb:1-1 product:e1q model:SM_S921B device:e1q transport_id:2
            0123456789ABCDEF       unauthorized usb:1-2 transport_id:3
            emulator-5556          offline transport_id:4

        """.trimIndent()

        assertEquals(
            listOf(
                DeviceListing(id = "emulator-5554", name = "sdk gphone64 arm64", kind = DeviceKind.AndroidEmulator, osVersion = null),
                DeviceListing(id = "R5CT1234ABC", name = "SM S921B", kind = DeviceKind.AndroidDevice, osVersion = null),
            ),
            parseAdbDevices(output),
        )
    }

    @Test
    fun `simctl lists booted simulators with the OS version from their runtime`() {
        val json = """
            {"devices": {
              "com.apple.CoreSimulator.SimRuntime.iOS-18-5": [
                {"udid": "A1", "name": "iPhone 16", "state": "Booted"},
                {"udid": "A2", "name": "iPhone 16 Pro", "state": "Shutdown"}
              ],
              "com.apple.CoreSimulator.SimRuntime.watchOS-11-5": []
            }}
        """.trimIndent()

        assertEquals(
            listOf(DeviceListing(id = "A1", name = "iPhone 16", kind = DeviceKind.IosSimulator, osVersion = "iOS 18.5")),
            parseBootedSimulators(json),
        )
    }

    @Test
    fun `simctl output that is not JSON lists nothing`() {
        assertEquals(emptyList(), parseBootedSimulators("xcrun: error: unable to find utility \"simctl\""))
    }

    @Test
    fun `idb lists physical devices and leaves simulators to simctl`() {
        val output = """
            Takumi's iPhone | 00008110-00161D9801DA801E | Booted | device | iOS 26.6.1 | arm64e | No Companion Connected
            iPhone 16 | 0A1B2C3D-4E5F | Shutdown | simulator | iOS 18.5 | arm64 | No Companion Connected
            Apple TV | 37E509F4-ECF8 | Shutdown | simulator | tvOS 18.5 | x86_64 | No Companion Connected
        """.trimIndent()

        assertEquals(
            listOf(DeviceListing(id = "00008110-00161D9801DA801E", name = "Takumi's iPhone", kind = DeviceKind.IosDevice, osVersion = "iOS 26.6.1")),
            parseIdbDevices(output),
        )
    }

    @Test
    fun `a size override set on the device wins over its physical size`() {
        assertEquals(IntSize(1080, 2400), parseWmSize("Physical size: 1080x2400\n"))
        assertEquals(IntSize(720, 1600), parseWmSize("Physical size: 1080x2400\nOverride size: 720x1600\n"))
        assertNull(parseWmSize("error: no devices/emulators found"))
    }

    @Test
    fun `idb describe gives the screen in pixels and its pixels per point`() {
        val json = """{"screen_dimensions": {"width": 1206, "height": 2622, "density": 3.0, "width_points": 402, "height_points": 874}}"""

        assertEquals(IdbScreen(IntSize(1206, 2622), pixelsPerPoint = 3.0), parseIdbScreen(json))
    }

    @Test
    fun `text for adb input text has its shell characters and spaces escaped`() {
        assertEquals("""a%sb\&c\%d""", escapeForAdbInputText("a b&c%d"))
    }

    @Test
    fun `text with a line break is refused rather than passed to the device shell`() {
        assertFailsWith<DeviceControlException> { escapeForAdbInputText("hello\nreboot") }
        assertFailsWith<DeviceControlException> { escapeForAdbInputText("a\rb") }
    }
}
