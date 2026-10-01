package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidDeviceControllerTest {
    private val folder: File = Files.createTempDirectory("mirror-android").toFile()
    private val commands = File(folder, "commands.txt")
    private val dumpsysDisplay = File(folder, "dumpsys-display.txt")

    // Answers the two reads the controller makes and logs every command it is given.
    private val fakeAdb = File(folder, "adb").apply {
        writeText(
            """
            #!/bin/sh
            echo "$*" >> '${commands.path}'
            case "$*" in
              *"dumpsys display"*) cat '${dumpsysDisplay.path}' ;;
              *"wm size"*) echo 'Physical size: 1080x2364' ;;
            esac
            """.trimIndent() + "\n",
        )
        setExecutable(true)
    }

    private val controller = AndroidDeviceController(adbPath = fakeAdb.path, serial = "device-1", emulatorScreens = null, ffmpegPath = "ffmpeg")

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `a folded foldable is mirrored from the cover panel its default display has moved to`() {
        // The default display itself reads OFF here while the cover panel behind it is on.
        assertEquals(AndroidDisplay(logicalId = 0, physicalId = COVER_PANEL), parseActiveAndroidDisplay(FOLDED))
    }

    @Test
    fun `an unfolded foldable is mirrored from the inner panel`() {
        assertEquals(AndroidDisplay(logicalId = 0, physicalId = INNER_PANEL), parseActiveAndroidDisplay(UNFOLDED))
    }

    @Test
    fun `a foldable with every panel off keeps to its default display`() {
        assertEquals(AndroidDisplay(logicalId = 0, physicalId = INNER_PANEL), parseActiveAndroidDisplay(dumpsysDisplayOf("asleep")))
    }

    @Test
    fun `a foldable whose default display's panel is off is mirrored from a display whose panel is on`() {
        val coverShownApart = listOf(
            panel(INNER_PANEL, state = "OFF"),
            panel(COVER_PANEL, state = "ON"),
            logicalDisplay(0, INNER_PANEL),
            logicalDisplay(2, COVER_PANEL),
        ).joinToString("\n")

        assertEquals(AndroidDisplay(logicalId = 2, physicalId = COVER_PANEL), parseActiveAndroidDisplay(coverShownApart))
    }

    @Test
    fun `a device with one panel names no display`() {
        assertNull(parseActiveAndroidDisplay(ONE_PANEL))
        assertNull(parseActiveAndroidDisplay(""))
    }

    @Test
    fun `a foldable's panel is named to screencap and screenrecord and its display to input and wm`() = runBlocking {
        dumpsysDisplay.writeText(FOLDED)

        controller.screenSize()
        controller.captureScreenshot()
        controller.tap(10, 20)
        controller.swipe(fromX = 1, fromY = 2, toX = 3, toY = 4, durationMillis = 250)
        controller.pressButton(DeviceButton.Home)
        controller.inputText("hello")
        (controller.openVideoStream(wanted = null) as VideoStream.H264).process.waitFor()

        val given = commands.readLines().filterNot { "dumpsys" in it }
        assertEquals(
            listOf(
                "-s device-1 shell wm size -d 0",
                "-s device-1 exec-out screencap -p -d $COVER_PANEL",
                "-s device-1 shell input -d 0 tap 10 20",
                "-s device-1 shell input -d 0 swipe 1 2 3 4 250",
                "-s device-1 shell input -d 0 keyevent KEYCODE_HOME",
                "-s device-1 shell input -d 0 text hello",
                "-s device-1 exec-out screenrecord --output-format=h264 --display-id $COVER_PANEL --time-limit 180 -",
            ),
            given,
        )
    }

    @Test
    fun `a device with one panel is given the commands without a display`() = runBlocking {
        dumpsysDisplay.writeText(ONE_PANEL)

        controller.screenSize()
        controller.captureScreenshot()
        controller.tap(10, 20)

        val given = commands.readLines().filterNot { "dumpsys" in it }
        assertEquals(listOf("-s device-1 shell wm size", "-s device-1 exec-out screencap -p", "-s device-1 shell input tap 10 20"), given)
    }

    @Test
    fun `reading the screen size follows a fold to the panel that is on`() = runBlocking {
        dumpsysDisplay.writeText(UNFOLDED)
        controller.captureScreenshot()
        dumpsysDisplay.writeText(FOLDED)

        assertEquals(IntSize(1080, 2364), controller.screenSize())
        controller.captureScreenshot()

        val screenshots = commands.readLines().filter { "screencap" in it }
        assertEquals(listOf("-s device-1 exec-out screencap -p -d $INNER_PANEL", "-s device-1 exec-out screencap -p -d $COVER_PANEL"), screenshots)
        assertEquals(2, commands.readLines().count { "dumpsys" in it })
    }
}

private const val INNER_PANEL = "4619827259835644672"

private const val COVER_PANEL = "4619827551948147201"

/** What `dumpsys display`, filtered as the controller asks, printed on a foldable emulator in [state]. */
private fun dumpsysDisplayOf(state: String): String = checkNotNull(AndroidDeviceControllerTest::class.java.getResource("/dumpsys-display/$state.txt")).readText()

private val FOLDED = dumpsysDisplayOf("folded")

private val UNFOLDED = dumpsysDisplayOf("unfolded")

private val ONE_PANEL = listOf(panel(INNER_PANEL, state = "ON"), logicalDisplay(0, INNER_PANEL)).joinToString("\n")

/** A physical panel's line in `dumpsys display`, cut down to the fields around the ones read. */
private fun panel(id: String, state: String) = """  DisplayDeviceInfo{"Built-in Screen": uniqueId="local:$id", 1080 x 2364, modeId 2, density 390, 390.0 x 390.0 dpi, touch INTERNAL, rotation 0, type INTERNAL, address StablePhysical{id=$id, port=1}, state $state, committedState $state, frameRateOverride , FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY, FLAG_ROTATES_WITH_CONTENT, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_TRUSTED}"""

/** A logical display's line in `dumpsys display`, showing the panel with [panelId], cut down likewise. */
private fun logicalDisplay(displayId: Int, panelId: String) = """    mOverrideDisplayInfo=DisplayInfo{"Built-in Screen", displayId $displayId, displayGroupId 0, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_TRUSTED, real 1080 x 2364, rotation 0, state ON, committedState ON, type INTERNAL, uniqueId "local:$panelId", app 1080 x 2364, density 390 (390.0 x 390.0) dpi, layerStack 0}"""
