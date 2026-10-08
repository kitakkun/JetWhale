package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MirrorCapturesTest {
    private val folder: File = Files.createTempDirectory("mirror-captures").toFile()
    private val copied = File(folder, "copied")
    private val scope = CoroutineScope(Dispatchers.Default)

    // Takes a while over the capture named slow, as osascript does over a large screenshot, and logs each path it puts.
    private val fakeOsascript = File(folder, "osascript").apply {
        writeText("#!/bin/sh\ncase \"$5\" in *slow*) sleep 0.5 ;; esac\nprintf '%s\\n' \"$5\" >> '${copied.absolutePath}'\n")
        setExecutable(true)
    }

    private val captures = MirrorCaptures(
        defaultRoot = File(folder, "captures"),
        storage = null,
        scope = scope,
        zone = ZoneOffset.UTC,
        notices = MirrorNotices(scope),
        ffmpegPath = CompletableDeferred(value = null),
        clipboard = CaptureClipboard(osascriptPath = fakeOsascript.absolutePath),
    )

    @AfterTest
    fun cleanUp() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        folder.deleteRecursively()
    }

    // A copy shows only in what the osascript process wrote: MirrorCaptures gives no signal when one
    // has landed, so the test watches the file for it.
    @Suppress("KOTRAIL_TEST_REAL_TIME_WAIT")
    @Test
    fun `copies reach the clipboard in the order they were asked for`() = runBlocking {
        assumeShellScriptsLaunch()
        val slow = screenshot("slow.png")
        val quick = screenshot("quick.png")

        captures.copy(slow)
        captures.copy(quick)
        withTimeout(COPIES_TIMEOUT_MILLIS) {
            while (!copied.exists() || copied.readLines().size < 2) delay(20)
        }

        assertEquals(listOf(slow.file.absolutePath, quick.file.absolutePath), copied.readLines())
    }

    private fun screenshot(name: String) = Capture(
        File(folder, name).apply { writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)) },
        CaptureInfo(
            deviceId = "emulator-5554",
            deviceName = "Pixel 9",
            platform = "Android",
            deviceKind = "Emulator",
            osVersion = null,
            kind = CaptureKind.Screenshot,
            widthPx = null,
            heightPx = null,
            capturedAtEpochMillis = 0,
            durationMillis = null,
        ),
    )
}

private const val COPIES_TIMEOUT_MILLIS = 5_000L
