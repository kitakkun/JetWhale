package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MirrorNoticeTest {
    private val folder: File = Files.createTempDirectory("mirror-notices").toFile()
    private val capture = Capture(File(folder, "125424-screenshot.png").apply { writeText("png") }, info("emulator-5554", "Pixel 9"))

    @Test
    fun `a saved screenshot offers to open that capture`() {
        val notice = MirrorNotice.saved(capture)

        assertEquals("Saved 125424-screenshot.png", notice.message)
        assertEquals(listOf<NoticeAction>(NoticeAction.OpenCapture(capture)), notice.actions)
        assertTrue(!notice.isError)
    }

    @Test
    fun `screenshots of every device saved offer the captures panel`() {
        val notice = MirrorNotice.screenshotsSaved(listOf(ScreenshotResult.Saved(capture), ScreenshotResult.Saved(capture)))

        assertEquals("Saved 2 screenshots", notice.message)
        assertEquals(listOf<NoticeAction>(NoticeAction.OpenCaptures), notice.actions)
    }

    @Test
    fun `a partial failure stays, says why, and retries only the devices that failed`() {
        val failed = ScreenshotResult.Failed(deviceId = "00008110-DEVICE", deviceName = "iPhone 13", reason = "the device is locked")

        val notice = MirrorNotice.screenshotsSaved(listOf(ScreenshotResult.Saved(capture), failed))

        assertEquals("Saved 1 of 2 screenshots", notice.message)
        assertTrue(notice.isError)
        assertEquals(listOf(NoticeAction.OpenCaptures, NoticeAction.RetryScreenshots(listOf("00008110-DEVICE"))), notice.actions)
        assertEquals(listOf("iPhone 13: the device is locked"), notice.details)
    }

    @Test
    fun `a single failed screenshot names the device and retries it`() {
        val notice = MirrorNotice.screenshotsSaved(listOf(ScreenshotResult.Failed(deviceId = "emulator-5554", deviceName = "Pixel 9", reason = "adb is gone")))

        assertEquals("Could not save a screenshot of Pixel 9: adb is gone", notice.message)
        assertEquals(listOf<NoticeAction>(NoticeAction.RetryScreenshots(listOf("emulator-5554"))), notice.actions)
    }

    @Test
    fun `a success leaves on its own and a failure stays until dismissed`() {
        val scope = TestScope(StandardTestDispatcher())
        val notices = MirrorNotices(scope)

        notices.show(MirrorNotice.saved(capture))
        scope.advanceTimeBy(SUCCESS_NOTICE_MILLIS + 1)
        scope.runCurrent()
        assertNull(notices.current)

        val failure = MirrorNotice.failure("Could not start recording", retry = null)
        notices.show(failure)
        scope.advanceTimeBy(SUCCESS_NOTICE_MILLIS * 10)
        scope.runCurrent()
        assertSame(failure, notices.current)

        notices.dismiss()
        assertNull(notices.current)
    }

    @Test
    fun `a held notice waits until it is released, then gets its full time again`() {
        val scope = TestScope(StandardTestDispatcher())
        val notices = MirrorNotices(scope)
        val saved = MirrorNotice.saved(capture)

        notices.show(saved)
        notices.hold(true)
        scope.advanceTimeBy(SUCCESS_NOTICE_MILLIS * 3)
        scope.runCurrent()
        assertSame(saved, notices.current)

        notices.hold(false)
        scope.advanceTimeBy(SUCCESS_NOTICE_MILLIS - 1)
        scope.runCurrent()
        assertSame(saved, notices.current)
        scope.advanceTimeBy(2)
        scope.runCurrent()
        assertNull(notices.current)
    }

    @Test
    fun `an older notice's time running out never takes away a newer one`() {
        val scope = TestScope(StandardTestDispatcher())
        val notices = MirrorNotices(scope)
        val newer = MirrorNotice.info("Copied the path")

        notices.show(MirrorNotice.saved(capture))
        scope.advanceTimeBy(SUCCESS_NOTICE_MILLIS - 10)
        notices.show(newer)
        scope.advanceTimeBy(20)
        scope.runCurrent()

        assertSame(newer, notices.current)
    }

    @Test
    fun `open goes to the panel for a connected device, to the folder otherwise, and nowhere once the file is gone`() {
        assertEquals(CaptureDestination.Panel, destinationOf(capture, connectedDeviceIds = setOf("emulator-5554")))
        assertEquals(CaptureDestination.Folder, destinationOf(capture, connectedDeviceIds = emptySet()))

        val gone = capture.copy(file = File(folder, "deleted.png"))
        assertEquals(CaptureDestination.Missing, destinationOf(gone, connectedDeviceIds = setOf("emulator-5554")))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the notice's buttons open, dismiss, and show the reasons`() = runComposeUiTest {
        val notice = MirrorNotice.screenshotsSaved(
            listOf(ScreenshotResult.Saved(capture), ScreenshotResult.Failed(deviceId = "00008110-DEVICE", deviceName = "iPhone 13", reason = "the device is locked")),
        )
        val recorded = RecordingNotices(notice)
        setContent { JwTheme(darkTheme = true) { MirrorNoticeHost(recorded) } }

        onNodeWithText("Open Captures").performClick()
        onNodeWithText("Retry").performClick()
        assertEquals(listOf(NoticeAction.OpenCaptures, NoticeAction.RetryScreenshots(listOf("00008110-DEVICE"))), recorded.performed)

        onNodeWithText("Details").performClick()
        onNodeWithText("iPhone 13: the device is locked").assertExists()

        onNodeWithContentDescription("Dismiss").performClick()
        assertEquals(1, recorded.dismissals)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `esc dismisses a focused notice`() = runComposeUiTest {
        val recorded = RecordingNotices(MirrorNotice.failure("Could not start recording", retry = null))
        setContent { JwTheme(darkTheme = true) { MirrorNoticeHost(recorded) } }

        // Key events reach the strip from whichever of its parts has focus.
        onNodeWithContentDescription("Dismiss").requestFocus()
        onNodeWithContentDescription("Dismiss").performKeyInput { pressKey(Key.Escape) }

        assertEquals(1, recorded.dismissals)
    }
}

private class RecordingNotices(override val notice: MirrorNotice) : MirrorNoticeActions {
    val performed = mutableListOf<NoticeAction>()
    var dismissals = 0

    override fun perform(action: NoticeAction) {
        performed += action
    }

    override fun dismiss() {
        dismissals++
    }

    override fun hold(held: Boolean) = Unit
}

private fun info(deviceId: String, deviceName: String) = CaptureInfo(
    deviceId = deviceId,
    deviceName = deviceName,
    platform = "Android",
    deviceKind = "Emulator",
    osVersion = null,
    kind = CaptureKind.Screenshot,
    widthPx = null,
    heightPx = null,
    capturedAtEpochMillis = 0,
    durationMillis = null,
)
