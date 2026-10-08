package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunner
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerButton
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerException
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerScreen
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerStartException
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerTarget
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunners
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class IosInputTest {
    private val folder: File = Files.createTempDirectory("ios-input").toFile()
    private val runners = FakeXcTestRunners()
    private val idbCalls = File(folder, "idb-calls")

    /** An idb stand-in for a 402x874-point simulator at 3x that notes each call. */
    private val fakeIdb = File(folder, "idb").apply {
        writeText(
            """
            #!/bin/sh
            echo "${'$'}*" >> '${idbCalls.path}'
            case "${'$'}1" in
              describe) echo '{"screen_dimensions":{"width":1206,"height":2622,"density":3}}' ;;
            esac
            """.trimIndent() + "\n",
        )
        setExecutable(true)
    }

    @AfterTest
    fun deleteFolder() {
        folder.deleteRecursively()
    }

    @Test
    fun `a simulator's input goes through its XCTest runner, in points, and not idb`() = runTest {
        val simulator = simulator(idbPath = fakeIdb.path)

        simulator.tap(x = 603, y = 1311)
        simulator.swipe(fromX = 300, fromY = 2400, toX = 300, toY = 1200, durationMillis = 250)
        simulator.inputText("hello")
        simulator.pressButton(DeviceButton.Home)
        simulator.pressButton(DeviceButton.Power)

        assertEquals(listOf("tap 201.0,437.0", "swipe 100.0,800.0 -> 100.0,400.0 in 250", "type hello", "press Home", "press Lock"), runners.runner.calls)
        assertEquals(setOf("SIM-1"), runners.askedFor.map(XcTestRunnerTarget::udid).toSet())
        assertFalse(idbCalls.exists())
    }

    @Test
    fun `a simulator falls back to idb when its runner cannot start`() = runTest {
        assumeShellScriptsLaunch()
        runners.startFailure = "the XCTest runner did not start: error: something broke"

        simulator(idbPath = fakeIdb.path).tap(x = 603, y = 1311)

        assertEquals(listOf("describe --udid SIM-1 --json", "ui tap --udid SIM-1 201 437"), idbCalls.readLines())
    }

    @Test
    fun `without idb, a runner that cannot start is the reason the input failed`() = runTest {
        runners.startFailure = "the XCTest runner did not start: error: something broke"

        val failure = assertFailsWith<DeviceControlException> { simulator(idbPath = null).inputText("hello") }

        assertEquals("the XCTest runner did not start: error: something broke", failure.message)
    }

    @Test
    fun `a command the runner refuses fails with the runner's reason and does not fall back to idb`() = runTest {
        runners.runner.refusal = "the XCTest runner failed to typeText: this Xcode's XCTest has no text-input events"

        val failure = assertFailsWith<DeviceControlException> { simulator(idbPath = fakeIdb.path).inputText("hello") }

        assertEquals("the XCTest runner failed to typeText: this Xcode's XCTest has no text-input events", failure.message)
        assertFalse(idbCalls.exists())
    }

    @Test
    fun `Recent apps on a simulator goes through idb, since no runner command opens the switcher`() = runTest {
        assumeShellScriptsLaunch()

        simulator(idbPath = fakeIdb.path).pressButton(DeviceButton.Recents)

        assertEquals(listOf("ui button --udid SIM-1 HOME", "ui button --udid SIM-1 HOME"), idbCalls.readLines())
        assertTrue(runners.runner.calls.isEmpty())
    }

    @Test
    fun `a simulator without idb offers the buttons its runner presses`() = runTest {
        assertEquals(listOf(DeviceButton.Home, DeviceButton.Power), simulator(idbPath = null).capabilities.buttons)
    }

    @Test
    fun `an iPhone's input is refused with the reason the runners give, until they give none`() = runTest {
        runners.refusal = "driving an iPhone needs your Apple development team"
        val iphone = iphone()
        assertEquals("driving an iPhone needs your Apple development team", iphone.capabilities.inputRefusal)
        assertTrue(iphone.capabilities.buttons.isEmpty())
        assertEquals("driving an iPhone needs your Apple development team", assertFailsWith<DeviceControlException> { iphone.tap(x = 10, y = 10) }.message)

        runners.refusal = null

        assertNull(iphone.capabilities.inputRefusal)
        assertEquals(listOf(DeviceButton.Home, DeviceButton.Power, DeviceButton.VolumeUp, DeviceButton.VolumeDown), iphone.capabilities.buttons)
    }

    @Test
    fun `an iPhone's input goes to the runner of that device`() = runTest {
        val iphone = iphone()

        iphone.pressButton(DeviceButton.VolumeUp)

        assertEquals(listOf("press VolumeUp"), runners.runner.calls)
        assertTrue(runners.askedFor.single() is XcTestRunnerTarget.Device)
    }

    @Test
    fun `an iPhone whose runner cannot start says why`() = runTest {
        runners.startFailure = "Developer Mode is off on the iPhone"

        val failure = assertFailsWith<DeviceControlException> { iphone().inputText("hi") }

        assertEquals("Developer Mode is off on the iPhone", failure.message)
    }

    private fun simulator(idbPath: String?) = IosSimulatorDeviceController(udid = "SIM-1", xcrunPath = "xcrun", idbPath = idbPath, runnerInput = XcTestRunnerInput(runners))

    private fun TestScope.iphone() = IosPhysicalDeviceController(
        udid = "00008110",
        idbPath = "idb",
        companions = IdbCompanions(
            idbCompanionPath = "idb_companion",
            idbPath = "idb",
            launcher = { throw deviceControlError("no companions in tests") },
            commands = { },
            ports = { 10_000 },
            idleTimeout = 3.minutes,
            scope = backgroundScope,
        ),
        ffmpegPath = null,
        runnerInput = XcTestRunnerInput(runners),
    )
}

/** Runners that hand out [runner] for every target, or refuse as told. */
private class FakeXcTestRunners : XcTestRunners {
    val runner = FakeXcTestRunner()
    val askedFor = mutableListOf<XcTestRunnerTarget>()
    var refusal: String? = null
    var startFailure: String? = null

    override fun refusalFor(target: XcTestRunnerTarget): String? = refusal

    override suspend fun runnerFor(target: XcTestRunnerTarget): XcTestRunner {
        askedFor += target
        startFailure?.let { throw XcTestRunnerStartException(it, null) }
        return runner
    }

    override fun startRunnerInBackground(target: XcTestRunnerTarget) = Unit
}

/** A runner on a 1206x2622-pixel screen at 3x that notes what it is told, or refuses with [refusal]. */
private class FakeXcTestRunner : XcTestRunner {
    val calls = mutableListOf<String>()
    var refusal: String? = null

    override val screen = XcTestRunnerScreen(widthPixels = 1206, heightPixels = 2622, scale = 3.0)

    override suspend fun tap(x: Double, y: Double) = note("tap $x,$y")

    override suspend fun longPress(x: Double, y: Double, durationMillis: Int) = note("long press $x,$y for $durationMillis")

    override suspend fun swipe(fromX: Double, fromY: Double, toX: Double, toY: Double, durationMillis: Int) = note("swipe $fromX,$fromY -> $toX,$toY in $durationMillis")

    override suspend fun typeText(text: String) = note("type $text")

    override suspend fun pressButton(button: XcTestRunnerButton) = note("press ${button.name}")

    override suspend fun activateApp(bundleId: String) = note("activate $bundleId")

    private fun note(call: String) {
        refusal?.let { throw XcTestRunnerException(it, null) }
        calls += call
    }
}
