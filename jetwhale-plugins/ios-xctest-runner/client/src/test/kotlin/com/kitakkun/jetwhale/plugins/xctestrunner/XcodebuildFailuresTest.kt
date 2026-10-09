package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XcodebuildFailuresTest {
    private val device = RunnerDestination.Device("00008110", developmentTeam = "ABCDE12345")
    private val simulator = RunnerDestination.Simulator("SIM-1")

    @Test
    fun `a device with Developer Mode off is told where to turn it on`() {
        val reason = XcodebuildFailures.reasonOf("xcodebuild: error: Developer Mode disabled\n", device)

        assertEquals("Developer Mode is off on the iPhone: turn it on in Settings → Privacy & Security → Developer Mode, then restart it (xcodebuild: error: Developer Mode disabled)", reason)
    }

    @Test
    fun `a locked device is told to stay unlocked`() {
        assertTrue(XcodebuildFailures.reasonOf("error: The device is passcode protected.", device).startsWith("the iPhone is locked"))
    }

    @Test
    fun `a device with UI automation off is told where to turn it on`() {
        assertTrue(XcodebuildFailures.reasonOf("error: Timed out while enabling automation mode.", device).startsWith("UI automation is off on the iPhone"))
    }

    @Test
    fun `a device xcodebuild cannot find is told to connect and trust`() {
        assertTrue(XcodebuildFailures.reasonOf("xcodebuild: error: Unable to find a destination matching the provided destination specifier", device).startsWith("xcodebuild cannot reach the iPhone"))
    }

    @Test
    fun `a signing failure names the team`() {
        assertTrue(XcodebuildFailures.reasonOf("error: No profiles for 'com.kitakkun.jetwhale.xctestrunner.abcde12345.uitests' were found", device).startsWith("the XCTest runner could not be signed for team ABCDE12345"))
    }

    @Test
    fun `a simulator's failure quotes xcodebuild's error lines rather than guessing at a device setting`() {
        val output = "Building\nerror: Unable to find a destination matching the provided destination specifier\n** TEST EXECUTE FAILED **"

        assertEquals("the XCTest runner did not start: error: Unable to find a destination matching the provided destination specifier", XcodebuildFailures.reasonOf(output, simulator))
    }

    @Test
    fun `output without an error line is quoted from its end`() {
        assertEquals("the XCTest runner did not start: one / two / three", XcodebuildFailures.reasonOf("zero\none\n\ntwo\nthree\n", simulator))
    }
}
