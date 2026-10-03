package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * An Android emulator or device, driven through the adb at [adbPath]. An emulator's screen comes
 * from its own gRPC stream through [emulatorScreens] when it has one, and from screenrecord
 * otherwise, which the ffmpeg at [ffmpegPath] decodes; without ffmpeg there is no stream, and the
 * mirror shows screenshots instead.
 *
 * On a device with more than one panel, such as a foldable, every screen command names the display
 * that is on. Without one, screencap writes a warning ahead of its PNG, and screenrecord frames the
 * picture at another panel's size.
 */
internal class AndroidDeviceController(
    private val adbPath: String,
    private val serial: String,
    private val emulatorScreens: EmulatorScreens?,
    private val ffmpegPath: String?,
) : DeviceController {
    override val capabilities = DeviceCapabilities(
        input = true,
        buttons = listOf(DeviceButton.Home, DeviceButton.Back, DeviceButton.Recents, DeviceButton.Power, DeviceButton.VolumeUp, DeviceButton.VolumeDown),
        recording = true,
        screenPower = true,
    )

    @Volatile
    private var displayReading: DisplayReading? = null

    // exec-out keeps the PNG binary-safe; `shell` would pass it through a pty that rewrites line ends.
    override suspend fun captureScreenshot(): ByteArray = runCommandChecked(adbPath, "-s", serial, "exec-out", "screencap", "-p", *panelArguments(option = "-d", display = display())).stdout

    // Reads the display afresh, since the mirror polls the size to notice a fold or a rotation. `wm
    // size` reports the upright size, which taps on a turned screen do not use, so it is only a
    // fallback.
    override suspend fun screenSize(): IntSize {
        val reading = readDisplay()
        return reading.size
            ?: parseWmSize(runCommandChecked(adbPath, "-s", serial, "shell", "wm", "size", *displayArguments(reading.display)).stdoutText)
            ?: throw deviceControlError("'adb shell wm size' reported no screen size")
    }

    override suspend fun tap(x: Int, y: Int) {
        runCommandChecked(adbPath, "-s", serial, "shell", "input", *displayArguments(display()), "tap", "$x", "$y")
    }

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) {
        runCommandChecked(adbPath, "-s", serial, "shell", "input", *displayArguments(display()), "swipe", "$fromX", "$fromY", "$toX", "$toY", "$durationMillis")
    }

    override suspend fun pressButton(button: DeviceButton) {
        runCommandChecked(adbPath, "-s", serial, "shell", "input", *displayArguments(display()), "keyevent", androidKeycodeOf(button))
    }

    override suspend fun inputText(text: String) {
        runCommandChecked(adbPath, "-s", serial, "shell", "input", *displayArguments(display()), "text", escapeForAdbInputText(text))
    }

    override suspend fun screenPower(): ScreenPower {
        // A grep that matches nothing exits non-zero, so the exit code is not checked.
        val result = runCommand(adbPath, "-s", serial, "shell", "dumpsys power | grep mWakefulness=; dumpsys window | grep isKeyguardShowing=")
        return parseScreenPower(result.stdoutText)
            ?: throw deviceControlError("could not read the screen state of $serial: ${result.stderr.ifBlank { result.stdoutText }.trim().take(200)}")
    }

    override suspend fun wake() {
        runCommandChecked(adbPath, "-s", serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
        // Dismisses only a lock screen without a PIN, pattern or password; one with them stays.
        runCommandChecked(adbPath, "-s", serial, "shell", "wm", "dismiss-keyguard")
    }

    override suspend fun sleep() {
        runCommandChecked(adbPath, "-s", serial, "shell", "input", "keyevent", "KEYCODE_SLEEP")
    }

    // screenrecord ends a session after 180 seconds; the mirror opens a new stream when it does.
    override suspend fun openVideoStream(wanted: IntSize?): VideoStream = withContext(Dispatchers.IO) {
        // The emulator streams its own framebuffer, which follows only its own posture
        // (`adb emu fold`). `cmd device_state state` moves the display to the other panel without
        // it, and the stream would stretch that panel's picture to the framebuffer's shape.
        val emulatorStream = emulatorScreens?.takeUnless {
            display() != null && isPostureOverridden(runCommand(adbPath, "-s", serial, "shell", "dumpsys device_state | grep -e mBaseState= -e mCommittedState=").stdoutText)
        }?.open(serial, wanted)
        emulatorStream ?: run {
            val ffmpegPath = ffmpegPath ?: throw deviceControlError("ffmpeg was not found, so the screen is shown through screenshots. $FFMPEG_INSTALL")
            val panel = panelArguments(option = "--display-id", display = display())
            VideoStream.H264(SystemProcessLauncher.start(listOf(adbPath, "-s", serial, "exec-out", "screenrecord", "--output-format=h264", *panel, "--time-limit", "$SCREENRECORD_TIME_LIMIT_SECONDS", "-")), ffmpegPath)
        }
    }

    override suspend fun startRecording(outputFile: File): DeviceRecording {
        val remotePath = "/sdcard/${outputFile.name}"
        val panel = panelArguments(option = "--display-id", display = display())
        val process = withContext(Dispatchers.IO) {
            SystemProcessLauncher.start(listOf(adbPath, "-s", serial, "shell", "screenrecord", *panel, "--time-limit", "$SCREENRECORD_TIME_LIMIT_SECONDS", remotePath))
        }
        return object : DeviceRecording {
            override suspend fun stop(): File = withContext(Dispatchers.IO) {
                // SIGINT lets screenrecord finish the mp4; killing the local adb client would leave
                // it unplayable. The path pattern spares the screenrecord that feeds the mirror, and
                // pkill's exit code is ignored because the recorder may already have hit its limit.
                runCommand(adbPath, "-s", serial, "shell", "pkill", "-INT", "-f", remotePath)
                process.waitFor(10, TimeUnit.SECONDS)
                // The device writes the file out after the process exits.
                delay(500)
                try {
                    runCommandChecked(adbPath, "-s", serial, "pull", remotePath, outputFile.absolutePath)
                } finally {
                    runCommand(adbPath, "-s", serial, "shell", "rm", "-f", remotePath)
                }
                outputFile
            }
        }
    }

    override suspend fun release() = Unit

    private suspend fun display(): AndroidDisplay? {
        val reading = displayReading
        if (reading != null && System.nanoTime() - reading.readAtNanos < DISPLAY_READING_MAX_AGE_NANOS) return reading.display
        return readDisplay().display
    }

    private suspend fun readDisplay(): DisplayReading {
        // grep exits non-zero when nothing matches, which reads as a device with one panel.
        val output = runCommand(adbPath, "-s", serial, "shell", "dumpsys display | grep -F -e DisplayDeviceInfo -e mOverrideDisplayInfo").stdoutText
        val display = parseActiveAndroidDisplay(output)
        return DisplayReading(display, parseDisplaySize(output, displayId = display?.logicalId ?: DEFAULT_DISPLAY_ID), System.nanoTime()).also { displayReading = it }
    }

    /** `-d` and the logical id `input` and `wm` take, or nothing on a device with one panel. */
    private fun displayArguments(display: AndroidDisplay?): Array<String> = display?.let { arrayOf("-d", "${it.logicalId}") } ?: emptyArray()

    /** [option] and the panel id screencap and screenrecord take, or nothing on a device with one panel. */
    private fun panelArguments(option: String, display: AndroidDisplay?): Array<String> = display?.let { arrayOf(option, it.physicalId) } ?: emptyArray()

    private class DisplayReading(val display: AndroidDisplay?, val size: IntSize?, val readAtNanos: Long)
}

private const val DISPLAY_READING_MAX_AGE_NANOS = 2_000_000_000L

/**
 * One of an Android device's displays, by the two ids its tools take: `input` and `wm` its logical
 * id, and screencap and screenrecord the id of the panel behind it.
 */
internal data class AndroidDisplay(val logicalId: Int, val physicalId: String)

/**
 * The display to mirror in the `DisplayDeviceInfo` and `mOverrideDisplayInfo` lines of
 * `adb shell dumpsys display`, or null for a device with one panel, whose tools need no display
 * named. A foldable has two panels and moves its default display from one to the other as it folds;
 * the default display is taken while its panel is on, then any other display whose panel is on,
 * such as a cover screen shown beside a dark inner one, and the default display when every panel is
 * off. The panel's state decides because a logical display's own can lag behind it: a folded
 * device's default display reads OFF while the cover panel behind it is on.
 */
internal fun parseActiveAndroidDisplay(dumpsysDisplay: String): AndroidDisplay? {
    val lines = dumpsysDisplay.lines()
    val panelIsOn = lines.filter { "DisplayDeviceInfo{" in it }.mapNotNull { line ->
        val panelId = PANEL_ID.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
        panelId to (PANEL_STATE.find(line)?.groupValues?.get(1) == "ON")
    }.toMap()
    if (panelIsOn.size < 2) return null
    val displays = lines.filter { "mOverrideDisplayInfo=DisplayInfo{" in it }.mapNotNull { line ->
        val logicalId = LOGICAL_DISPLAY_ID.find(line)?.groupValues?.get(1)?.toInt() ?: return@mapNotNull null
        val physicalId = PANEL_ID.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
        AndroidDisplay(logicalId, physicalId)
    }
    val displaysOnLitPanels = displays.filter { panelIsOn[it.physicalId] == true }
    return displaysOnLitPanels.firstOrNull { it.logicalId == DEFAULT_DISPLAY_ID }
        ?: displaysOnLitPanels.firstOrNull()
        ?: displays.firstOrNull { it.logicalId == DEFAULT_DISPLAY_ID }
}

private const val DEFAULT_DISPLAY_ID = 0

/**
 * The size logical display [displayId] has now in the `mOverrideDisplayInfo` lines of
 * `adb shell dumpsys display`, or null when they do not give it. It is the `real` size: turned as
 * the screen is, and following a `wm size` override, the size `input` takes coordinates in and
 * `screencap` writes.
 */
internal fun parseDisplaySize(dumpsysDisplay: String, displayId: Int): IntSize? {
    val line = dumpsysDisplay.lines().firstOrNull { "mOverrideDisplayInfo=DisplayInfo{" in it && LOGICAL_DISPLAY_ID.find(it)?.groupValues?.get(1)?.toInt() == displayId } ?: return null
    val (width, height) = DISPLAY_REAL_SIZE.find(line)?.destructured ?: return null
    return IntSize(width.toInt(), height.toInt())
}

/** A physical panel's id, as `uniqueId="local:…"` in `DisplayDeviceInfo` and `uniqueId "local:…"` in `DisplayInfo`. */
private val PANEL_ID = Regex("""uniqueId[=\s]"local:(\d+)"""")

private val LOGICAL_DISPLAY_ID = Regex("""displayId (\d+)""")

private val PANEL_STATE = Regex(""", state (\w+)""")

private val DISPLAY_REAL_SIZE = Regex("""real (\d+) x (\d+)""")

/**
 * Whether the `mBaseState` and `mCommittedState` lines of `adb shell dumpsys device_state` show the
 * device in a posture other than its hardware's, as `cmd device_state state` puts it in; false
 * when they do not say.
 */
internal fun isPostureOverridden(dumpsysDeviceState: String): Boolean {
    val states = DEVICE_STATE.findAll(dumpsysDeviceState).associate { it.groupValues[1] to it.groupValues[2] }
    val base = states["mBaseState"] ?: return false
    val committed = states["mCommittedState"] ?: return false
    return base != committed
}

private val DEVICE_STATE = Regex("""(mBaseState|mCommittedState)=Optional\[DeviceState\{identifier=(\d+)""")

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

private const val SCREENRECORD_TIME_LIMIT_SECONDS = 180
