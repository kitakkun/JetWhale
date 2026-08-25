package com.kitakkun.jetwhale.plugins.androiddevice.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class TapCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.tap"
    override val description =
        "Taps a point on the screen. Coordinates are screen pixels unless unit is DP, and are " +
            "checked against the device's screen size — an off-screen tap is rejected instead of " +
            "being swallowed silently by the device."

    private val x by int("Horizontal coordinate, measured from the left edge.")
    private val y by int("Vertical coordinate, measured from the top edge.")
    private val unit by enumOrNull(UNIT_DESCRIPTION, CoordinateUnit.entries)

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val space = target.readCoordinateSpace()
        val unit = arguments[unit] ?: CoordinateUnit.PX
        val x = space.toPixels(arguments[x], unit)
        val y = space.toPixels(arguments[y], unit)
        space.requireOnScreen("x", x, "y", y)

        val result = target.shell("input", "tap", x.toString(), y.toString(), timeout = AdbTimeouts.SHELL)
        target.requireSuccess(result, "input tap was refused")?.let { return it }
        return target.successResult {
            put("x", x)
            put("y", y)
        }
    }
}

/**
 * The platform's long-press timeout is 400–500ms depending on the build; 800ms is safely past every
 * one of them without making a QA run noticeably slower.
 */
private const val DEFAULT_LONG_PRESS_MS = 800

@OptIn(ExperimentalJetWhaleApi::class)
internal class LongPressCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.longPress"
    override val description =
        "Presses and holds a point, as a zero-length swipe of a given duration. Use it for context " +
            "menus, drag handles and anything else a plain tap does not trigger."

    private val x by int("Horizontal coordinate, measured from the left edge.")
    private val y by int("Vertical coordinate, measured from the top edge.")
    private val unit by enumOrNull(UNIT_DESCRIPTION, CoordinateUnit.entries)
    private val durationMs by intOrNull(
        "How long to hold, in milliseconds. Defaults to $DEFAULT_LONG_PRESS_MS, which clears every " +
            "platform long-press timeout (400–500ms) with margin.",
    )

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val space = target.readCoordinateSpace()
        val unit = arguments[unit] ?: CoordinateUnit.PX
        val x = space.toPixels(arguments[x], unit)
        val y = space.toPixels(arguments[y], unit)
        space.requireOnScreen("x", x, "y", y)
        val durationMs = arguments[durationMs] ?: DEFAULT_LONG_PRESS_MS
        if (durationMs <= 0) throw JetWhaleMcpArgumentException("invalid durationMs: $durationMs (expected a positive integer)")

        val result = target.shell("input", "swipe", x.toString(), y.toString(), x.toString(), y.toString(), durationMs.toString(), timeout = AdbTimeouts.SHELL)
        target.requireSuccess(result, "input swipe was refused")?.let { return it }
        return target.successResult {
            put("x", x)
            put("y", y)
            put("durationMs", durationMs)
        }
    }
}

/** Long enough for the platform to read it as a drag rather than a fling, short enough not to stall a run. */
private const val DEFAULT_SWIPE_MS = 300

@OptIn(ExperimentalJetWhaleApi::class)
internal class SwipeCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.swipe"
    override val description =
        "Drags from one point to another. Both ends are checked against the device's screen size. " +
            "A shorter duration reads as a fling, a longer one as a deliberate drag."

    private val fromX by int("Horizontal coordinate to start from.")
    private val fromY by int("Vertical coordinate to start from.")
    private val toX by int("Horizontal coordinate to end at.")
    private val toY by int("Vertical coordinate to end at.")
    private val unit by enumOrNull(UNIT_DESCRIPTION, CoordinateUnit.entries)
    private val durationMs by intOrNull("How long the gesture takes, in milliseconds. Defaults to $DEFAULT_SWIPE_MS, which the platform reads as a drag rather than a fling.")

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val space = target.readCoordinateSpace()
        val unit = arguments[unit] ?: CoordinateUnit.PX
        val fromX = space.toPixels(arguments[fromX], unit)
        val fromY = space.toPixels(arguments[fromY], unit)
        val toX = space.toPixels(arguments[toX], unit)
        val toY = space.toPixels(arguments[toY], unit)
        space.requireOnScreen("fromX", fromX, "fromY", fromY)
        space.requireOnScreen("toX", toX, "toY", toY)
        val durationMs = arguments[durationMs] ?: DEFAULT_SWIPE_MS
        if (durationMs <= 0) throw JetWhaleMcpArgumentException("invalid durationMs: $durationMs (expected a positive integer)")

        val result = target.shell("input", "swipe", fromX.toString(), fromY.toString(), toX.toString(), toY.toString(), durationMs.toString(), timeout = AdbTimeouts.SHELL)
        target.requireSuccess(result, "input swipe was refused")?.let { return it }
        return target.successResult {
            put("fromX", fromX)
            put("fromY", fromY)
            put("toX", toX)
            put("toY", toY)
            put("durationMs", durationMs)
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class TypeCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.type"
    override val description =
        "Types text into whatever currently has focus — tap the field first. Spaces and shell " +
            "metacharacters are escaped for you. Only printable ASCII can be typed: `input text` " +
            "writes through the key character map, so anything else is rejected rather than sent as garbage."

    private val text by string("The text to type. Printable ASCII only.")

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val text = arguments[text]
        val unsupported = unsupportedInputTextCharacters(text)
        if (unsupported.isNotEmpty()) {
            throw JetWhaleMcpArgumentException(
                "invalid text: `input text` can only type printable ASCII, and this contains " +
                    unsupported.joinToString(", ") { "'$it' (U+%04X)".format(it.code) },
            )
        }

        val result = target.shell("input", "text", escapeForInputText(text), timeout = AdbTimeouts.SHELL)
        target.requireSuccess(result, "input text was refused")?.let { return it }
        return target.successResult {
            put("text", text)
            put("characters", text.length)
        }
    }
}

/** The key events a QA run reaches for; anything else goes through keyCode. */
internal enum class DeviceKey {
    BACK,
    HOME,
    ENTER,
    TAB,
    DELETE,
    ESCAPE,
    APP_SWITCH,
    POWER,
    VOLUME_UP,
    VOLUME_DOWN,
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT,
    DPAD_CENTER,
    MENU,
    SEARCH,
    CAMERA,
    WAKEUP,
    SLEEP,
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class KeyCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.key"
    override val description = "Sends one of the key events a QA run needs by name. For anything outside this list, use keyCode."

    private val key by enum("The key to send; it is sent as KEYCODE_<name>.", DeviceKey.entries)

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val keyCode = "KEYCODE_${arguments[key].name}"
        val result = target.shell("input", "keyevent", keyCode, timeout = AdbTimeouts.SHELL)
        target.requireSuccess(result, "input keyevent was refused")?.let { return it }
        return target.successResult { put("key", keyCode) }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class KeyCodeCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.keyCode"
    override val description = "Sends a raw Android key code, for the keys the `key` tool does not name."

    private val code by int("Android KeyEvent key code, e.g. 82 for KEYCODE_MENU.")

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val code = arguments[code]
        if (code < 0) throw JetWhaleMcpArgumentException("invalid code: $code (expected a non-negative integer)")
        val result = target.shell("input", "keyevent", code.toString(), timeout = AdbTimeouts.SHELL)
        target.requireSuccess(result, "input keyevent was refused")?.let { return it }
        return target.successResult { put("code", code) }
    }
}
