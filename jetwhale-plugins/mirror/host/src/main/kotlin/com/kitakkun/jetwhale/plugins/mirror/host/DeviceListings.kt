package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The devices in `adb devices -l` output that are ready for use. An emulator's serial starts with
 * `emulator-`; anything else is a device on USB or Wi-Fi. Offline and unauthorized ones are left
 * out: adb can do nothing with them until the user acts on the device.
 */
internal fun parseAdbDevices(output: String): List<DeviceListing> = output.lineSequence()
    .drop(1)
    .map { it.trim().split(Regex("\\s+")) }
    .filter { it.size >= 2 && it[1] == "device" }
    .map { tokens ->
        val serial = tokens[0]
        val model = tokens.firstOrNull { it.startsWith("model:") }?.removePrefix("model:")?.replace('_', ' ')
        DeviceListing(
            id = serial,
            name = model ?: serial,
            kind = if (serial.startsWith("emulator-")) DeviceKind.AndroidEmulator else DeviceKind.AndroidDevice,
            osVersion = null,
        )
    }
    .toList()

/**
 * The booted simulators in `xcrun simctl list devices booted -j` output. Runtimes are keyed by
 * identifiers like `com.apple.CoreSimulator.SimRuntime.iOS-18-5`, which give the OS version.
 */
internal fun parseBootedSimulators(json: String): List<DeviceListing> {
    val runtimes = try {
        Json.parseToJsonElement(json) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    }?.get("devices") as? JsonObject ?: return emptyList()
    return runtimes.flatMap { (runtime, devices) ->
        (devices as? JsonArray).orEmpty().mapNotNull { element ->
            val device = element as? JsonObject ?: return@mapNotNull null
            if (device["state"]?.jsonPrimitive?.content != "Booted") return@mapNotNull null
            val udid = device["udid"]?.jsonPrimitive?.content ?: return@mapNotNull null
            DeviceListing(
                id = udid,
                name = device["name"]?.jsonPrimitive?.content ?: udid,
                kind = DeviceKind.IosSimulator,
                osVersion = runtime.substringAfterLast('.').replaceFirst('-', ' ').replace('-', '.'),
            )
        }
    }
}

/**
 * The iOS devices connected to this Mac by USB, in the JSON that `xcrun devicectl list devices
 * --json-output <file>` writes, or null when the JSON is not such a list. devicectl lists
 * simulators too, which simctl already reports, and devices paired over the network, which idb and
 * `iproxy` reach only by USB; both are left out, and so are watches, TVs and Macs.
 */
internal fun parseDevicectlDevices(json: String): List<DeviceListing>? {
    val devices = try {
        Json.parseToJsonElement(json) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    }?.objectAt("result")?.get("devices") as? JsonArray ?: return null
    return devices.mapNotNull { element ->
        val properties = (element as? JsonObject)?.objectAt("properties") ?: return@mapNotNull null
        val hardware = properties.objectAt("hardware") ?: return@mapNotNull null
        val isUsbIosDevice = hardware.text("reality") != "simulated" && hardware.text("platform") == "iOS" && properties.objectAt("connection")?.text("transportType") == "wired"
        val udid = hardware.text("udid")?.takeIf { isUsbIosDevice } ?: return@mapNotNull null
        DeviceListing(
            id = udid,
            name = properties.objectAt("state")?.text("name") ?: udid,
            kind = DeviceKind.IosDevice,
            osVersion = properties.objectAt("software")?.objectAt("osVersionNumber")?.text("stringValue")?.let { "iOS $it" },
        )
    }
}

private fun JsonObject.objectAt(key: String): JsonObject? = get(key) as? JsonObject

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

/**
 * The size in pixels of a simulator's screen as its interface is turned, from `xcrun simctl io <udid>
 * enumerate`: the integrated screen's pixel size, its sides swapped while its UI orientation is a
 * landscape one. Null when the output lists no integrated screen.
 */
internal fun parseSimulatorScreen(output: String): IntSize? {
    val screen = output.split(Regex("""\n\s*\(\d+\) """)).drop(1).firstOrNull { "Screen Type: Integrated" in it } ?: return null
    val (width, height) = Regex("""Pixel Size: \{(\d+), (\d+)\}""").find(screen)?.destructured ?: return null
    val size = IntSize(width.toInt(), height.toInt())
    return if (Regex("""UI Orientation: Landscape""").containsMatchIn(screen)) IntSize(size.height, size.width) else size
}

/**
 * The screen size in `adb shell wm size` output. An override set with `wm size WxH` is what the
 * device draws and takes input at, so it wins over the physical size.
 */
internal fun parseWmSize(output: String): IntSize? {
    val sizes = output.lineSequence().mapNotNull { line ->
        val match = Regex("""(Physical|Override) size: (\d+)x(\d+)""").find(line) ?: return@mapNotNull null
        match.groupValues[1] to IntSize(match.groupValues[2].toInt(), match.groupValues[3].toInt())
    }.toMap()
    return sizes["Override"] ?: sizes["Physical"]
}

/** The screen's size in pixels that `idb describe --json` reports, or null when it gives none: a physical device reports its sides as 0. */
internal fun parseIdbScreen(json: String): IntSize? {
    val screen = try {
        (Json.parseToJsonElement(json) as? JsonObject)?.get("screen_dimensions") as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    } ?: return null
    val width = screen["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
    val height = screen["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
    if (width <= 0 || height <= 0) return null
    return IntSize(width, height)
}
