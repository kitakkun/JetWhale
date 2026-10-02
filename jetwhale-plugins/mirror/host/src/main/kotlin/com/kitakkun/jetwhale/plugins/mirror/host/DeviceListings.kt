package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 * The physical iOS devices in `idb_companion --list 1` output, which has one JSON object per target,
 * or null when the output is in a form this does not read: no line is such an object, a target is of
 * a type other than a device or a simulator, or a device has no UDID. The caller then takes the
 * devices from idb instead. Simulators can be listed there too, but simctl already reports those.
 */
internal fun parseCompanionDevices(output: String): List<DeviceListing>? {
    val targets = output.lineSequence().filter(String::isNotBlank).mapNotNull { line ->
        try {
            Json.parseToJsonElement(line) as? JsonObject
        } catch (_: IllegalArgumentException) {
            null
        }
    }.toList()
    if (targets.isEmpty() && output.isNotBlank()) return null
    return targets.mapNotNull { target ->
        when (target.text("type")?.lowercase()) {
            "simulator" -> null

            "device" -> {
                val udid = target.text("udid") ?: return null
                DeviceListing(id = udid, name = target.text("name") ?: udid, kind = DeviceKind.IosDevice, osVersion = target.text("os_version"))
            }

            else -> return null
        }
    }
}

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.content

/**
 * The physical iOS devices in `idb list-targets` output, whose lines read
 * `name | udid | state | type | os | architecture | companion`. Simulators are listed there too,
 * but simctl already reports those.
 */
internal fun parseIdbDevices(output: String): List<DeviceListing> = output.lineSequence()
    .map { line -> line.split(" | ").map(String::trim) }
    .filter { fields -> fields.size >= 5 && fields[3] == "device" }
    .map { fields -> DeviceListing(id = fields[1], name = fields[0], kind = DeviceKind.IosDevice, osVersion = fields[4]) }
    .toList()

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

/** A screen as `idb describe --json` reports it: pixels, and pixels per point. */
internal data class IdbScreen(val size: IntSize, val pixelsPerPoint: Double)

/** The screen `idb describe --json` reports, or null when it gives none: a physical device reports its sides as 0. */
internal fun parseIdbScreen(json: String): IdbScreen? {
    val screen = try {
        (Json.parseToJsonElement(json) as? JsonObject)?.get("screen_dimensions") as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    } ?: return null
    val width = screen["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
    val height = screen["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
    val density = screen["density"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
    if (width <= 0 || height <= 0) return null
    return IdbScreen(IntSize(width, height), density)
}
