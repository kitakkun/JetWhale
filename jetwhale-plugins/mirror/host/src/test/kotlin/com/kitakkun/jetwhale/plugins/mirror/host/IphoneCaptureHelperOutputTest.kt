package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class IphoneCaptureHelperOutputTest {
    @Test
    fun `the helper's events are read from its JSON lines`() {
        assertEquals(IphoneCaptureHelperEvent.Started, parseIphoneCaptureHelperEvent("""{"event":"started","matchedBy":"name","name":"Test iPhone","uniqueId":"0x1100000005ac12a8"}"""))
        assertEquals(IphoneCaptureHelperEvent.Format(IntSize(1170, 2532)), parseIphoneCaptureHelperEvent("""{"event":"format","height":2532,"passthrough":true,"width":1170}"""))
        assertEquals(
            IphoneCaptureHelperEvent.Failed(reason = "deviceNotFound", message = "no capture device showed the iPhone within 15 s"),
            parseIphoneCaptureHelperEvent("""{"candidates":[],"event":"error","message":"no capture device showed the iPhone within 15 s","reason":"deviceNotFound"}"""),
        )
    }

    @Test
    fun `a line of plain text, broken JSON or an event this does not know is part of the log`() {
        assertNull(parseIphoneCaptureHelperEvent("jetwhale-iphone-capture: encoding frames to answer a key frame request"))
        assertNull(parseIphoneCaptureHelperEvent("""{"event":"format","width":1170"""))
        assertNull(parseIphoneCaptureHelperEvent("""{"event":"stats","frames":10}"""))
        assertNull(parseIphoneCaptureHelperEvent("""{"event":"format","width":"wide","height":2532}"""))
    }

    @Test
    fun `each failure the helper exits with says what to do about it`() {
        assertContains(iphoneCaptureHelperFailureMessage(3, "no capture device showed the iPhone within 15 s", emptyList()), "Connect it by USB, unlock it and trust this Mac")
        assertContains(iphoneCaptureHelperFailureMessage(3, "no capture device showed the iPhone within 15 s", emptyList()), "within 15 s")
        assertContains(iphoneCaptureHelperFailureMessage(4, "denied", emptyList()), "System Settings → Privacy & Security → Camera")
        assertContains(iphoneCaptureHelperFailureMessage(5, "the capture session did not start", emptyList()), "could not be captured: the capture session did not start")
        assertContains(iphoneCaptureHelperFailureMessage(2, "unknown argument --fps", emptyList()), "refused its arguments: unknown argument --fps")
    }

    @Test
    fun `a helper stopped by a signal is explained by the Camera usage macOS requires`() {
        val message = iphoneCaptureHelperFailureMessage(134, null, emptyList())

        assertContains(message, "signal 6")
        assertContains(message, "declares no Camera usage")
        assertContains(iphoneCaptureHelperFailureMessage(137, null, emptyList()), "signal 9")
    }

    @Test
    fun `without a reported failure the last lines of the helper's log explain it`() {
        val message = iphoneCaptureHelperFailureMessage(70, reportedFailureMessage = null, logTail = listOf("first", "second"))

        assertEquals("The iPhone capture helper ended with exit code 70: first / second", message)
        assertFalse(iphoneCaptureHelperFailureMessage(70, reportedFailureMessage = null, logTail = emptyList()).endsWith(": "))
    }

    @Test
    fun `the exit codes match the ones the bundled helper source defines`() {
        val source = readIphoneCaptureHelperSource().decodeToString()
        val helperExitCodes = Regex("""case (\w+) = (\d+)""").findAll(source.substringAfter("enum HelperExit").substringBefore("var reason")).associate { it.groupValues[1] to it.groupValues[2].toInt() }

        assertEquals(IphoneCaptureHelperExit.entries.associate { it.name.replaceFirstChar(Char::lowercase) to it.code }, helperExitCodes)
    }
}
