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
    fun `devicectl lists the iPhones and iPads on USB and leaves simulators and those on the network out`() {
        assertEquals(
            listOf(
                DeviceListing(id = "00008110-000A1B2C3D4E5F60", name = "Test iPhone", kind = DeviceKind.IosDevice, osVersion = "iOS 26.6.1"),
                DeviceListing(id = "00008112-0001A2B3C4D5E6F7", name = "Test iPad", kind = DeviceKind.IosDevice, osVersion = "iOS 26.6.1"),
            ),
            parseDevicectlDevices(readResourceText("/devicectl/list-devices.json")),
        )
    }

    @Test
    fun `devicectl output that is not a list of devices is not taken for no devices`() {
        assertNull(parseDevicectlDevices("xcrun: error: unable to find utility \"devicectl\""))
        assertNull(parseDevicectlDevices("""{"info":{"outcome":"failed"}}"""))
        assertEquals(emptyList(), parseDevicectlDevices("""{"result":{"devices":[]}}"""))
    }

    @Test
    fun `a device devicectl lists on USB that is simulated or not iOS or disconnected or has no UDID is left out`() {
        val json = """
            {"result":{"devices":[
              {"properties":{"hardware":{"platform":"iOS","reality":"simulated","udid":"5C9E2B7A-0000-4000-8000-000000000001"},"connection":{"transportType":"wired"}}},
              {"properties":{"hardware":{"platform":"watchOS","udid":"00008301-0000000000000001"},"connection":{"transportType":"wired"}}},
              {"properties":{"hardware":{"platform":"iOS"},"connection":{"transportType":"wired"}}},
              {"properties":{"hardware":{"platform":"iOS","udid":"00008110-0000000000000002"},"connection":{"transportType":"wired","state":"disconnected"}}}
            ]}}
        """.trimIndent()

        assertEquals(emptyList(), parseDevicectlDevices(json))
    }

    @Test
    fun `a simulator's screen is its pixel size turned the way simctl says its interface is`() {
        assertEquals(IntSize(1206, 2622), parseSimulatorScreen(readResourceText("/simctl-io-enumerate/portrait.txt")))
        assertEquals(IntSize(2622, 1206), parseSimulatorScreen(readResourceText("/simctl-io-enumerate/landscape-left.txt")))
        assertNull(parseSimulatorScreen("Invalid device: SIM-1"))
    }

    @Test
    fun `a size override set on the device wins over its physical size`() {
        assertEquals(IntSize(1080, 2400), parseWmSize("Physical size: 1080x2400\n"))
        assertEquals(IntSize(720, 1600), parseWmSize("Physical size: 1080x2400\nOverride size: 720x1600\n"))
        assertNull(parseWmSize("error: no devices/emulators found"))
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

    private fun readResourceText(path: String): String = checkNotNull(DeviceListingsTest::class.java.getResource(path)) { "$path is missing" }.readText()
}
