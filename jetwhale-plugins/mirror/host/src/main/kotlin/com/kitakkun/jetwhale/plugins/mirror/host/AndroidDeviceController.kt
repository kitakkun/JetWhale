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
        buttons = listOf(DeviceButton.Home, DeviceButton.Back, DeviceButton.Power, DeviceButton.VolumeUp, DeviceButton.VolumeDown),
        recording = true,
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
        val keycode = when (button) {
            DeviceButton.Home -> "KEYCODE_HOME"
            DeviceButton.Back -> "KEYCODE_BACK"
            DeviceButton.Power -> "KEYCODE_POWER"
            DeviceButton.VolumeUp -> "KEYCODE_VOLUME_UP"
            DeviceButton.VolumeDown -> "KEYCODE_VOLUME_DOWN"
        }
        runCommandChecked(adb, "-s", serial, "shell", "input", "keyevent", keycode)
    }

    override suspend fun inputText(text: String) {
        runCommandChecked(adb, "-s", serial, "shell", "input", "text", escapeForAdbInputText(text))
    }

    // screenrecord ends a session after 180 seconds; the mirror opens a new stream when it does.
    override suspend fun openVideoStream(): Process = withContext(Dispatchers.IO) {
        SystemProcessLauncher.start(listOf(adb, "-s", serial, "exec-out", "screenrecord", "--output-format=h264", "--time-limit", "180", "-"))
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
 * literal percent sign is escaped before spaces are encoded.
 */
internal fun escapeForAdbInputText(text: String): String = text
    .replace(Regex("""([\\'"`$&*()\[\]{}+|<>;?~#!])"""), """\\$1""")
    .replace("%", "\\%")
    .replace(" ", "%s")
