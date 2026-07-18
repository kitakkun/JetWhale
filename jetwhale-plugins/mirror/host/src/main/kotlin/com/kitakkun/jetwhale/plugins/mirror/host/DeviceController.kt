package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readBytes

enum class DevicePlatform { ANDROID, IOS }

enum class DeviceButton { HOME, BACK, POWER, VOLUME_UP, VOLUME_DOWN }

/** One connected Android emulator/device or booted iOS simulator. */
data class MirrorDevice(
    val id: String,
    val name: String,
    val platform: DevicePlatform,
    val controller: DeviceController,
)

/**
 * Drives one device: screenshots and input events. All coordinates are in **screenshot pixels**
 * (the controller converts to the platform's native input unit internally), so on-screen positions
 * in a captured frame map 1:1 to input coordinates.
 */
interface DeviceController {
    suspend fun captureScreenshot(): ByteArray

    suspend fun tap(x: Int, y: Int)

    suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int)

    suspend fun pressButton(button: DeviceButton)

    suspend fun inputText(text: String)
}

internal class AndroidDeviceController(
    private val adbPath: String,
    private val serial: String,
) : DeviceController {
    override suspend fun captureScreenshot(): ByteArray {
        // exec-out keeps the PNG stream binary-safe (no pty CRLF mangling like `shell` would do).
        return runCommandChecked(adbPath, "-s", serial, "exec-out", "screencap", "-p").stdout
    }

    override suspend fun tap(x: Int, y: Int) {
        runCommandChecked(adbPath, "-s", serial, "shell", "input", "tap", "$x", "$y")
    }

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) {
        runCommandChecked(adbPath, "-s", serial, "shell", "input", "swipe", "$fromX", "$fromY", "$toX", "$toY", "$durationMillis")
    }

    override suspend fun pressButton(button: DeviceButton) {
        val keycode = when (button) {
            DeviceButton.HOME -> "KEYCODE_HOME"
            DeviceButton.BACK -> "KEYCODE_BACK"
            DeviceButton.POWER -> "KEYCODE_POWER"
            DeviceButton.VOLUME_UP -> "KEYCODE_VOLUME_UP"
            DeviceButton.VOLUME_DOWN -> "KEYCODE_VOLUME_DOWN"
        }
        runCommandChecked(adbPath, "-s", serial, "shell", "input", "keyevent", keycode)
    }

    override suspend fun inputText(text: String) {
        // `adb shell` re-parses arguments through the device shell, so shell metacharacters must
        // be escaped; `input text` additionally requires spaces encoded as %s.
        val escaped = text
            .replace(Regex("""([\\'"`$&*()\[\]{}+|<>;?~#!])"""), """\\$1""")
            .replace(" ", "%s")
        runCommandChecked(adbPath, "-s", serial, "shell", "input", "text", escaped)
    }
}

/**
 * iOS simulator controller. Screenshots go through `xcrun simctl`; input events need Facebook's
 * `idb` companion tool (simctl has no input API) and fail with a clear message when it is absent.
 */
internal class IosDeviceController(
    private val udid: String,
    private val idbPath: String?,
) : DeviceController {
    // Pixels-per-point factor of the simulator screen, fetched lazily from `idb describe`.
    private var cachedPixelsPerPoint: Double? = null

    override suspend fun captureScreenshot(): ByteArray {
        val tempFile = createTempFile(prefix = "jetwhale-mirror-", suffix = ".png")
        try {
            runCommandChecked("xcrun", "simctl", "io", udid, "screenshot", tempFile.toAbsolutePath().toString())
            return tempFile.readBytes()
        } finally {
            tempFile.deleteIfExists()
        }
    }

    override suspend fun tap(x: Int, y: Int) {
        val idb = requireIdb()
        val scale = pixelsPerPoint()
        runCommandChecked(idb, "ui", "tap", "--udid", udid, "${(x / scale).toInt()}", "${(y / scale).toInt()}")
    }

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) {
        val idb = requireIdb()
        val scale = pixelsPerPoint()
        runCommandChecked(
            idb, "ui", "swipe", "--udid", udid,
            "--duration", "${durationMillis / 1000.0}",
            "${(fromX / scale).toInt()}", "${(fromY / scale).toInt()}",
            "${(toX / scale).toInt()}", "${(toY / scale).toInt()}",
        )
    }

    override suspend fun pressButton(button: DeviceButton) {
        val idbButton = when (button) {
            DeviceButton.HOME -> "HOME"

            DeviceButton.POWER -> "LOCK"

            DeviceButton.BACK -> throw DeviceControlException("iOS has no BACK button")

            DeviceButton.VOLUME_UP, DeviceButton.VOLUME_DOWN ->
                throw DeviceControlException("volume buttons are not controllable on the iOS simulator")
        }
        runCommandChecked(requireIdb(), "ui", "button", "--udid", udid, idbButton)
    }

    override suspend fun inputText(text: String) {
        runCommandChecked(requireIdb(), "ui", "text", "--udid", udid, text)
    }

    private fun requireIdb(): String = idbPath
        ?: throw DeviceControlException("iOS simulator input requires idb (https://fbidb.io). Install it with: brew install idb-companion && pipx install fb-idb")

    private suspend fun pixelsPerPoint(): Double {
        cachedPixelsPerPoint?.let { return it }
        val output = runCommandChecked(requireIdb(), "describe", "--udid", udid, "--json").stdoutText
        val screen = try {
            Json.parseToJsonElement(output).jsonObject["screen_dimensions"]!!.jsonObject
        } catch (e: Exception) {
            throw DeviceControlException("could not read screen dimensions from 'idb describe'", e)
        }
        val density = screen["density"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: throw DeviceControlException("'idb describe' reported no screen density")
        cachedPixelsPerPoint = density
        return density
    }
}

/** Lists connected Android devices/emulators and booted iOS simulators. */
internal class DeviceDiscovery {
    private val adbPath: String by lazy { findAdbPath() }
    private val idbPath: String? by lazy { findIdbPath() }

    suspend fun discoverDevices(): List<MirrorDevice> = discoverAndroidDevices() + discoverIosSimulators()

    private suspend fun discoverAndroidDevices(): List<MirrorDevice> {
        val result = try {
            runCommandChecked(adbPath, "devices", "-l")
        } catch (_: DeviceControlException) {
            return emptyList()
        }
        return result.stdoutText.lineSequence()
            .drop(1) // "List of devices attached" header
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val tokens = line.trim().split(Regex("\\s+"))
                val serial = tokens.getOrNull(0) ?: return@mapNotNull null
                if (tokens.getOrNull(1) != "device") return@mapNotNull null
                val model = tokens.firstOrNull { it.startsWith("model:") }?.removePrefix("model:")?.replace('_', ' ')
                MirrorDevice(
                    id = serial,
                    name = model ?: serial,
                    platform = DevicePlatform.ANDROID,
                    controller = AndroidDeviceController(adbPath = adbPath, serial = serial),
                )
            }
            .toList()
    }

    private suspend fun discoverIosSimulators(): List<MirrorDevice> {
        if (!File("/usr/bin/xcrun").exists()) return emptyList()
        val result = try {
            runCommandChecked("xcrun", "simctl", "list", "devices", "booted", "-j")
        } catch (_: DeviceControlException) {
            return emptyList()
        }
        val devicesByRuntime = try {
            Json.parseToJsonElement(result.stdoutText).jsonObject["devices"]?.jsonObject ?: return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        return devicesByRuntime.values.flatMap { runtimeDevices ->
            (runtimeDevices as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { element ->
                val device = element.jsonObject
                if (device["state"]?.jsonPrimitive?.content != "Booted") return@mapNotNull null
                val udid = device["udid"]?.jsonPrimitive?.content ?: return@mapNotNull null
                MirrorDevice(
                    id = udid,
                    name = device["name"]?.jsonPrimitive?.content ?: udid,
                    platform = DevicePlatform.IOS,
                    controller = IosDeviceController(udid = udid, idbPath = idbPath),
                )
            }
        }
    }
}
