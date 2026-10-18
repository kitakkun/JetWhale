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
    fun `an iPhone that does not trust the runner's developer is told where to trust it`() {
        val output = """
            IDELaunchReport: … Finished with error: The application could not be launched because the Developer App Certificate is not trusted.
            ERROR: The operation couldn?t be completed. (CoreDeviceCLISupport.DiagnoseError error 0.)
            JetWhaleRunnerUITests-Runner encountered an error (Failed to install or launch the test )
        """.trimIndent()

        val reason = XcodebuildFailures.reasonOf(output, device)

        assertTrue(reason.startsWith("the iPhone does not trust the developer that signed the XCTest runner yet: on the iPhone, open Settings → General → VPN & Device Management"), reason)
        assertTrue(XcodebuildFailures.reasonOf("$output\nerror: the runner's code signature is invalid", device).startsWith("the iPhone does not trust the developer"))
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
    fun `an Xcode whose license has not been accepted is told the command that accepts it`() {
        val output = "You have not agreed to the Xcode license agreements. Please run 'sudo xcodebuild -license' from within a Terminal window to review and agree to the Xcode and Apple SDKs license."

        assertEquals("Xcode's license has not been accepted: run 'sudo xcodebuild -license accept' in Terminal, then try again", XcodebuildFailures.xcodeSetupReasonOf(output))
        assertTrue(XcodebuildFailures.reasonOf(output, simulator).startsWith("Xcode's license has not been accepted"))
    }

    @Test
    fun `a Mac whose developer directory is the Command Line Tools is told to install or choose Xcode`() {
        val output = "xcode-select: error: tool 'xcodebuild' requires Xcode, but active developer directory '/Library/Developer/CommandLineTools' is a command line tools instance"

        assertEquals(
            "the XCTest runner needs Xcode, and xcodebuild cannot run: install Xcode from the App Store, or choose it with 'sudo xcode-select -s /Applications/Xcode.app', then try again",
            XcodebuildFailures.xcodeSetupReasonOf(output),
        )
    }

    @Test
    fun `a Mac without developer tools is told to install Xcode`() {
        val output = "xcrun: error: invalid active developer path (/Library/Developer/CommandLineTools), missing xcrun at: /Library/Developer/CommandLineTools/usr/bin/xcrun"

        assertTrue(XcodebuildFailures.xcodeSetupReasonOf(output).orEmpty().startsWith("the XCTest runner needs Xcode"))
    }

    @Test
    fun `a build error is not taken for a missing Xcode`() {
        assertEquals(null, XcodebuildFailures.xcodeSetupReasonOf("error: iOS 26.2 Platform Not Installed."))
    }

    @Test
    fun `output without an error line is quoted from its end`() {
        assertEquals("the XCTest runner did not start: one / two / three", XcodebuildFailures.reasonOf("zero\none\n\ntwo\nthree\n", simulator))
    }
}
