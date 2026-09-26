package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** An Android emulator or device, driven through adb. */
internal class AndroidDeviceController(
    private val adb: String,
    private val serial: String,
) : DeviceController {
    override val capabilities = DeviceCapabilities(
        input = true,
        buttons = listOf(DeviceButton.Home, DeviceButton.Back, DeviceButton.Recents, DeviceButton.Power, DeviceButton.VolumeUp, DeviceButton.VolumeDown),
        recording = true,
        screenPower = true,
    )

    // exec-out keeps the PNG binary-safe; `shell` would pass it through a pty that rewrites line ends.
    override suspend fun captureScreenshot(): ByteArray = runCommandChecked(adb, "-s", serial, "exec-out", "screencap", "-p").stdout

    override suspend fun screenSize(): IntSize = parseWmSize(runCommandChecked(adb, "-s", serial, "shell", "wm", "size").stdoutText)
        ?: throw deviceControlError("'adb shell wm size' reported no screen size")

    override suspend fun tap(x: Int, y: Int) {
        runCommandChecked(adb, "-s", serial, "shell", "input", "tap", "$x", "$y")
    }

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) {
        runCommandChecked(adb, "-s", serial, "shell", "input", "swipe", "$fromX", "$fromY", "$toX", "$toY", "$durationMillis")
    }

    override suspend fun pressButton(button: DeviceButton) {
        runCommandChecked(adb, "-s", serial, "shell", "input", "keyevent", androidKeycodeOf(button))
    }

    override suspend fun inputText(text: String) {
        runCommandChecked(adb, "-s", serial, "shell", "input", "text", escapeForAdbInputText(text))
    }

    override suspend fun screenPower(): ScreenPower {
        // One round trip for both; `dumpsys window` is large, so the device filters it. A grep that
        // matches nothing exits non-zero, which is why the exit code is not checked.
        val result = runCommand(adb, "-s", serial, "shell", "dumpsys power | grep mWakefulness=; dumpsys window | grep isKeyguardShowing=")
        return parseScreenPower(result.stdoutText)
            ?: throw deviceControlError("could not read the screen state of $serial: ${result.stderr.ifBlank { result.stdoutText }.trim().take(200)}")
    }

    override suspend fun wake() {
        runCommandChecked(adb, "-s", serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
        // Dismisses only a lock screen without a PIN, pattern or password; one with them stays.
        runCommandChecked(adb, "-s", serial, "shell", "wm", "dismiss-keyguard")
    }

    override suspend fun sleep() {
        runCommandChecked(adb, "-s", serial, "shell", "input", "keyevent", "KEYCODE_SLEEP")
    }

    // screenrecord ends a session after 180 seconds; the mirror opens a new stream when it does.
    override suspend fun openVideoStream(wanted: IntSize?): VideoStream = withContext(Dispatchers.IO) {
        VideoStream.H264(SystemProcessLauncher.start(listOf(adb, "-s", serial, "exec-out", "screenrecord", "--output-format=h264", "--time-limit", "180", "-")))
    }

    override suspend fun startRecording(outputFile: File): DeviceRecording {
        val remotePath = "/sdcard/${outputFile.name}"
        val process = withContext(Dispatchers.IO) {
            SystemProcessLauncher.start(listOf(adb, "-s", serial, "shell", "screenrecord", "--time-limit", "180", remotePath))
        }
        return object : DeviceRecording {
            override suspend fun stop(): File = withContext(Dispatchers.IO) {
                // SIGINT lets screenrecord finish the mp4; killing the local adb client would leave
                // it unplayable. The path pattern spares the screenrecord that feeds the mirror, and
                // pkill's exit code is ignored because the recorder may already have hit its limit.
                runCommand(adb, "-s", serial, "shell", "pkill", "-INT", "-f", remotePath)
                process.waitFor(10, TimeUnit.SECONDS)
                // The device writes the file out after the process exits.
                delay(500)
                try {
                    runCommandChecked(adb, "-s", serial, "pull", remotePath, outputFile.absolutePath)
                } finally {
                    runCommand(adb, "-s", serial, "shell", "rm", "-f", remotePath)
                }
                outputFile
            }
        }
    }

    override suspend fun release() = Unit
}

/**
 * [text] as `adb shell input text` needs it: the device shell parses the argument again, so its
 * metacharacters are escaped, and `input text` reads `%` as an escape (a space is `%s`), so a
 * literal percent sign is escaped before spaces are encoded. A line break would end the command
 * in that shell and start another, and no escape carries it through, so control characters are
 * refused.
 */
internal fun escapeForAdbInputText(text: String): String {
    if (text.any(Char::isISOControl)) throw deviceControlError("text with a line break, tab or other control character cannot be typed on an Android device; type each line separately")
    return text
        .replace(Regex("""([\\'"`$&*()\[\]{}+|<>;?~#!])"""), """\\$1""")
        .replace("%", "\\%")
        .replace(" ", "%s")
}

/**
 * The screen state in the output of `dumpsys power` and `dumpsys window`, or null when it has no
 * wakefulness line. `Awake` and `Dreaming` (a screensaver) have the screen on; `Asleep` and
 * `Dozing` (an always-on display) show nothing the mirror can use.
 */
internal fun parseScreenPower(output: String): ScreenPower? {
    val wakefulness = Regex("""mWakefulness=(\w+)""").find(output)?.groupValues?.get(1) ?: return null
    val locked = Regex("""isKeyguardShowing=(\w+)""").find(output)?.groupValues?.get(1) == "true"
    return ScreenPower(awake = wakefulness == "Awake" || wakefulness == "Dreaming", locked = locked)
}

/** The `input keyevent` code that presses [button] on an Android device. */
internal fun androidKeycodeOf(button: DeviceButton): String = when (button) {
    DeviceButton.Home -> "KEYCODE_HOME"
    DeviceButton.Back -> "KEYCODE_BACK"
    DeviceButton.Recents -> "KEYCODE_APP_SWITCH"
    DeviceButton.Power -> "KEYCODE_POWER"
    DeviceButton.VolumeUp -> "KEYCODE_VOLUME_UP"
    DeviceButton.VolumeDown -> "KEYCODE_VOLUME_DOWN"
}
