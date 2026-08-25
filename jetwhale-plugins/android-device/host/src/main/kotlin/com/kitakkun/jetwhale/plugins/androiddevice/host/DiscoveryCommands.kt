package com.kitakkun.jetwhale.plugins.androiddevice.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbUnavailableException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListDevicesCommand(private val adb: JetWhaleAdb) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.listDevices"
    override val description =
        "Lists the Android devices and emulators adb can see: [{serial, state, model, product, " +
            "transportId, isEmulator}]. Only a device in state \"device\" accepts commands; its " +
            "\"serial\" is what every other tool here takes. Start a QA run with this."

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
        val run = AdbRun(adb)
        val result = try {
            run.exec("devices", "-l", timeout = AdbTimeouts.QUICK)
        } catch (e: JetWhaleAdbUnavailableException) {
            return adbUnavailableResult(e)
        }
        if (result.exitCode != 0) {
            return JetWhaleMcpResult.text(
                buildJsonObject {
                    put("ok", false)
                    put("error", "`adb devices -l` failed")
                    put("exitCode", result.exitCode)
                    put("output", result.output.trim())
                    put("adb", run.invocations.toJson())
                }.toString(),
            )
        }
        val devices = parseAdbDevices(result.output)
        return JetWhaleMcpResult.text(
            buildJsonObject {
                putJsonArray("devices") {
                    devices.forEach { device ->
                        addJsonObject {
                            put("serial", device.serial)
                            put("state", device.state)
                            put("model", device.model)
                            put("product", device.product)
                            put("transportId", device.transportId)
                            put("isEmulator", device.isEmulator)
                        }
                    }
                }
                put("adb", run.invocations.toJson())
            }.toString(),
        )
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class DeviceInfoCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.deviceInfo"
    override val description =
        "Reports what a device is and what its screen is: model, manufacturer, Android release and " +
            "SDK level, screen size in pixels, density, and current rotation (0=portrait, 1=90° " +
            "counter-clockwise, 2=180°, 3=270°). The size is what tap/swipe coordinates are validated against."

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val properties = target.shell("getprop", timeout = AdbTimeouts.QUICK).let { if (it.exitCode == 0) parseGetProps(it.output) else emptyMap() }
        val space = target.readCoordinateSpace()
        val rotation = target.shell("dumpsys", "window", "displays", timeout = AdbTimeouts.SHELL)
            .let { if (it.exitCode == 0) parseRotation(it.output) else null }

        return JetWhaleMcpResult.text(
            target.resultJson {
                put("state", target.device.state)
                put("isEmulator", target.device.isEmulator)
                put("model", properties["ro.product.model"])
                put("manufacturer", properties["ro.product.manufacturer"])
                put("androidRelease", properties["ro.build.version.release"])
                put("sdkInt", properties["ro.build.version.sdk"]?.toIntOrNull())
                putJsonObject("screen") {
                    put("width", space.size?.width)
                    put("height", space.size?.height)
                    put("density", space.density)
                }
                put("rotation", rotation)
            },
        )
    }
}

/** adb's own `wait-for-device` waits forever, so the tool carries the deadline instead. */
private const val DEFAULT_WAIT_SECONDS = 60

@OptIn(ExperimentalJetWhaleApi::class)
internal class WaitForDeviceCommand(private val adb: JetWhaleAdb) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.waitForDevice"
    override val description =
        "Waits for a device to be connected and finished booting (sys.boot_completed=1), then " +
            "returns its serial. Use it after starting an emulator or rebooting a device, before " +
            "installing or launching anything."

    // Not the shared serial parameter: this tool is the one that runs before a device is usable,
    // so it cannot resolve its target up front the way the others do.
    private val serial by stringOrNull(SERIAL_DESCRIPTION)
    private val timeoutSeconds by intOrNull(
        "How long to wait, in seconds. Defaults to $DEFAULT_WAIT_SECONDS, because adb's own wait has no timeout at all and a stuck boot would hang the call forever.",
    )

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
        val serial = arguments[this.serial]
        val timeoutSeconds = arguments[timeoutSeconds] ?: DEFAULT_WAIT_SECONDS
        if (timeoutSeconds <= 0) throw JetWhaleMcpArgumentException("invalid timeoutSeconds: $timeoutSeconds (expected a positive integer)")
        val budget = timeoutSeconds.seconds
        val startedAt = TimeSource.Monotonic.markNow()

        val run = AdbRun(adb)
        val target = if (serial == null) emptyArray() else arrayOf("-s", serial)
        try {
            val connected = run.exec(*target, "wait-for-device", timeout = budget)
            if (connected.exitCode != 0) {
                return JetWhaleMcpResult.text(
                    buildJsonObject {
                        put("ok", false)
                        put("error", "wait-for-device failed")
                        put("exitCode", connected.exitCode)
                        put("output", connected.output.trim())
                        put("adb", run.invocations.toJson())
                    }.toString(),
                )
            }

            while (true) {
                val booted = run.exec(*target, "shell", "getprop", "sys.boot_completed", timeout = AdbTimeouts.QUICK)
                if (booted.exitCode == 0 && booted.output.trim() == "1") break
                if (startedAt.elapsedNow() >= budget) {
                    return JetWhaleMcpResult.text(
                        buildJsonObject {
                            put("ok", false)
                            put("error", "the device connected but sys.boot_completed did not become 1 within $timeoutSeconds seconds")
                            put("adb", run.invocations.toJson())
                        }.toString(),
                    )
                }
                delay(BOOT_POLL_INTERVAL)
            }

            val devices = run.exec("devices", "-l", timeout = AdbTimeouts.QUICK).let { parseAdbDevices(it.output) }
            val resolved = if (serial != null) serial else devices.singleOrNull { it.isUsable }?.serial
            return JetWhaleMcpResult.text(
                buildJsonObject {
                    put("ok", true)
                    put("serial", resolved)
                    put("waitedMs", startedAt.elapsedNow().inWholeMilliseconds)
                    put("adb", run.invocations.toJson())
                }.toString(),
            )
        } catch (e: JetWhaleAdbUnavailableException) {
            return adbUnavailableResult(e)
        }
    }

    private companion object {
        val BOOT_POLL_INTERVAL = 1.seconds
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class WakeCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.wake"
    override val description =
        "Wakes the screen and dismisses the keyguard, so a screenshot shows the app rather than a " +
            "black screen or the lock screen. Safe to call when the device is already awake."

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val wake = target.shell("input", "keyevent", "KEYCODE_WAKEUP", timeout = AdbTimeouts.SHELL)
        target.requireSuccess(wake, "KEYCODE_WAKEUP was refused")?.let { return it }
        val dismiss = target.shell("wm", "dismiss-keyguard", timeout = AdbTimeouts.SHELL)
        target.requireSuccess(dismiss, "wm dismiss-keyguard was refused")?.let { return it }
        return target.successResult()
    }
}

/** The rotations `settings put system user_rotation` accepts, plus letting the sensor decide. */
internal enum class DeviceRotation(val userRotation: Int?) {
    PORTRAIT(0),
    LANDSCAPE(1),
    REVERSE_PORTRAIT(2),
    REVERSE_LANDSCAPE(3),

    /** Hands rotation back to the accelerometer. */
    AUTO(null),
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class SetRotationCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.setRotation"
    override val description =
        "Pins the screen to a rotation, or hands it back to the accelerometer with AUTO. Pinning " +
            "keeps a QA run reproducible: an unpinned device rotates under the test and invalidates " +
            "every coordinate taken from an earlier screenshot."

    private val rotation by enum("The rotation to pin the screen to, or AUTO to follow the sensor again.", DeviceRotation.entries)

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val rotation = arguments[rotation]
        val accelerometer = if (rotation == DeviceRotation.AUTO) "1" else "0"
        val toggled = target.shell("settings", "put", "system", "accelerometer_rotation", accelerometer, timeout = AdbTimeouts.SHELL)
        target.requireSuccess(toggled, "could not write accelerometer_rotation")?.let { return it }

        rotation.userRotation?.let { userRotation ->
            val applied = target.shell("settings", "put", "system", "user_rotation", userRotation.toString(), timeout = AdbTimeouts.SHELL)
            target.requireSuccess(applied, "could not write user_rotation")?.let { return it }
        }
        return target.successResult {
            put("rotation", rotation.name)
            put("userRotation", rotation.userRotation)
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class SetAnimationsCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.setAnimations"
    override val description =
        "Turns the system's window, transition and animator scales on or off. Turning them off makes " +
            "a QA run stable: a screenshot taken mid-transition otherwise catches a half-drawn screen."

    private val enabled by boolean("true restores the scales to 1, false sets all three to 0.")

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val scale = if (arguments[enabled]) "1" else "0"
        for (setting in ANIMATION_SETTINGS) {
            val result = target.shell("settings", "put", "global", setting, scale, timeout = AdbTimeouts.SHELL)
            target.requireSuccess(result, "could not write $setting")?.let { return it }
        }
        return target.successResult {
            put("scale", scale.toInt())
            put("settings", JsonArray(ANIMATION_SETTINGS.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        }
    }

    private companion object {
        val ANIMATION_SETTINGS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
    }
}
