package com.kitakkun.jetwhale.plugins.mirror.host

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CaptureClipboardTest {
    private val folder: File = Files.createTempDirectory("mirror-clipboard").toFile()
    private val recording = Capture(File(folder, "123412-recording.mp4").apply { writeBytes(byteArrayOf(0, 0, 0, 0x18)) }, info(CaptureKind.Recording))

    // Records the capture's path and kind, which follow the script it is given.
    private val fakeOsascript = File(folder, "osascript").apply {
        writeText("#!/bin/sh\nprintf '%s\\n' \"$5\" \"$6\" > '${folder.absolutePath}/arguments'\n")
        setExecutable(true)
    }

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `on macOS the pasteboard script is given the capture's path and kind`() {
        CaptureClipboard(osascriptPath = fakeOsascript.absolutePath).putCapture(recording)

        assertEquals(listOf(recording.file.absolutePath, "Recording"), File(folder, "arguments").readLines())
    }

    @Test
    fun `a pasteboard the script cannot write fails with what osascript said`() {
        fakeOsascript.writeText("#!/bin/sh\necho 'execution error: Error: Error: the clipboard did not take it (-2700)' >&2\nexit 1\n")

        val failure = assertFailsWith<IOException> { CaptureClipboard(osascriptPath = fakeOsascript.absolutePath).putCapture(recording) }

        assertEquals("execution error: Error: Error: the clipboard did not take it (-2700)", failure.message)
    }

    @Test
    fun `a capture no longer in the folder is not copied`() {
        val gone = Capture(File(folder, "gone.png"), info(CaptureKind.Screenshot))

        assertFailsWith<FileNotFoundException> { CaptureClipboard(osascriptPath = fakeOsascript.absolutePath).putCapture(gone) }
        assertFailsWith<FileNotFoundException> { CaptureClipboard(osascriptPath = null).putCapture(gone) }
    }
}

private fun info(kind: CaptureKind) = CaptureInfo(
    deviceId = "emulator-5554",
    deviceName = "Pixel 9",
    platform = "Android",
    deviceKind = "Emulator",
    osVersion = null,
    kind = kind,
    widthPx = null,
    heightPx = null,
    capturedAtEpochMillis = 0,
    durationMillis = null,
)
