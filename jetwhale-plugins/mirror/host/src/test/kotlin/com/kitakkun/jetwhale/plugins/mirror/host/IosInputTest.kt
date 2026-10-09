package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunner
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerButton
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerException
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerInterfaceScreen
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerOrientation
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerPointSpace
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerScreen
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerStartException
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerTarget
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunners
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class IosInputTest {
    private val folder: File = Files.createTempDirectory("ios-input").toFile()
    private val runners = FakeXcTestRunners()

    /** An xcrun whose simctl writes a screenshot that reads `screenshot`. */
    private val fakeXcrun = File(folder, "xcrun").apply {
        writeText(
            """
            #!/bin/sh
            [ "${'$'}1 ${'$'}2 ${'$'}3 ${'$'}4" = 'simctl io SIM-1 screenshot' ] || exit 64
            printf screenshot > "${'$'}5"
            """.trimIndent() + "\n",
        )
        setExecutable(true)
    }

    /** The capture helpers started for iPhones, which report frames at once unless a test says otherwise. */
    private val helpers = CopyOnWriteArrayList<FakeCaptureHelperProcess>()
    private val firstHelper = CompletableDeferred<FakeCaptureHelperProcess>()
    private var helpersReportFrames = true

    /** How the capture helpers started for iPhones fail at once, or null when they run. */
    private var helpersFailWith: IphoneCaptureExit? = null
    private val captureScope = CoroutineScope(Job())

    @AfterTest
    fun deleteFolder() {
        helpers.forEach { it.exit(0) }
        captureScope.cancel()
        folder.deleteRecursively()
    }

    @Test
    fun `a simulator's input goes through its XCTest runner in the points of its screenshots`() = runTest {
        val simulator = simulator()
        assertEquals(listOf(DeviceButton.Home, DeviceButton.Recents, DeviceButton.Power), simulator.capabilities.buttons)

        simulator.tap(x = 603, y = 1311)
        simulator.swipe(fromX = 300, fromY = 2400, toX = 300, toY = 1200, durationMillis = 250)
        simulator.inputText("hello")
        simulator.pressButton(DeviceButton.Home)
        simulator.pressButton(DeviceButton.Recents)
        simulator.pressButton(DeviceButton.Power)

        assertEquals(
            listOf("tap 201.0,437.0 on screen", "swipe 100.0,800.0 -> 100.0,400.0 in 250 on screen", "type hello", "press Home", "open app switcher", "press Lock"),
            runners.runner.calls,
        )
        assertEquals(setOf("SIM-1"), runners.askedFor.map(XcTestRunnerTarget::udid).toSet())
    }

    @Test
    fun `a simulator whose runner cannot start says why`() = runTest {
        runners.startFailure = "the XCTest runner did not start: error: something broke"

        val failure = assertFailsWith<DeviceControlException> { simulator().inputText("hello") }

        assertEquals("the XCTest runner did not start: error: something broke", failure.message)
    }

    @Test
    fun `a command the runner refuses fails with the runner's reason`() = runTest {
        runners.runner.refusal = "the XCTest runner failed to typeText: this Xcode's XCTest has no text-input events"

        val failure = assertFailsWith<DeviceControlException> { simulator().inputText("hello") }

        assertEquals("the XCTest runner failed to typeText: this Xcode's XCTest has no text-input events", failure.message)
    }

    @Test
    fun `a simulator whose iOS the runner refuses has no input and no live video and says why`() = runTest {
        runners.refusal = "the XCTest runner needs iOS 17 or later, and this one runs iOS 16"
        val simulator = simulator()

        assertEquals("the XCTest runner needs iOS 17 or later, and this one runs iOS 16", simulator.capabilities.inputRefusal)
        assertEquals(emptyList(), simulator.capabilities.buttons)
        assertEquals("the XCTest runner needs iOS 17 or later, and this one runs iOS 16", assertFailsWith<DeviceControlException> { simulator.pressButton(DeviceButton.Recents) }.message)
        assertEquals("the XCTest runner needs iOS 17 or later, and this one runs iOS 16", assertFailsWith<DeviceControlException> { simulator.openVideoStream(wanted = null) }.message)
        assertTrue(runners.askedFor.isEmpty())
    }

    @Test
    fun `a simulator without Xcode's runners has no input and no live video and says why`() = runTest {
        val simulator = IosSimulatorDeviceController(udid = "SIM-1", iosMajorVersion = 26, xcrunPath = "xcrun", runnerInput = null)

        assertEquals("a simulator's live video and input need Xcode's xcodebuild, which was not found", simulator.capabilities.inputRefusal)
        assertEquals(emptyList(), simulator.capabilities.buttons)
        assertEquals("a simulator's live video and input need Xcode's xcodebuild, which was not found", assertFailsWith<DeviceControlException> { simulator.openVideoStream(wanted = null) }.message)
    }

    @Test
    fun `a simulator shows screenshots until its runner streams and then only the runner's frames`() = runBlocking {
        assumeShellScriptsLaunch()
        val firstScreenshotShown = CompletableDeferred<Unit>()
        runners.runner.screenFrames = flow {
            firstScreenshotShown.await()
            emit("runner-1".encodeToByteArray())
            emit("runner-2".encodeToByteArray())
        }
        val stream = simulator().openVideoStream(wanted = null) as VideoStream.EncodedImages
        val shown = mutableListOf<String>()

        withTimeout(10.seconds) {
            stream.collectUntilClosed { image ->
                shown += image.decodeToString()
                firstScreenshotShown.complete(Unit)
            }
        }

        assertEquals("screenshot", shown.first())
        assertEquals("runner-2", shown.last())
        assertTrue(shown.dropWhile { it == "screenshot" }.none { it == "screenshot" }, "a screenshot came after a frame of the runner: $shown")
        assertEquals(listOf(30), runners.runner.screenStreamRates)
    }

    @Test
    fun `a simulator's runner stream that fails ends the video with the runner's reason`() = runBlocking {
        assumeShellScriptsLaunch()
        runners.startFailure = "the XCTest runner did not start: error: something broke"
        val stream = simulator().openVideoStream(wanted = null) as VideoStream.EncodedImages

        val failure = assertFailsWith<DeviceControlException> { withTimeout(10.seconds) { stream.collectUntilClosed {} } }

        assertEquals("the XCTest runner did not start: error: something broke", failure.message)
    }

    @Test
    fun `a lease on a device's runner is taken through the runner and read back from the runners`() = runTest {
        val simulator = simulator()
        runners.keptAliveUntil = LEASE_END

        assertEquals(LEASE_END, simulator.keepRunnerAlive(30.minutes))
        assertEquals(listOf("keep alive for 30m"), runners.runner.calls)
        assertEquals(LEASE_END, simulator.runnerKeptAliveUntil())
    }

    @Test
    fun `an iPhone's input is refused with the reason the runners give, until they give none`() = runBlocking {
        runners.refusal = "driving an iPhone needs your Apple development team"
        val iphone = iphone(ffmpegPath = null)
        assertEquals("driving an iPhone needs your Apple development team", iphone.capabilities.inputRefusal)
        assertTrue(iphone.capabilities.buttons.isEmpty())
        assertEquals("driving an iPhone needs your Apple development team", assertFailsWith<DeviceControlException> { iphone.tap(x = 10, y = 10) }.message)

        runners.refusal = null

        assertNull(iphone.capabilities.inputRefusal)
        assertEquals(listOf(DeviceButton.Home, DeviceButton.Power, DeviceButton.VolumeUp, DeviceButton.VolumeDown), iphone.capabilities.buttons)
    }

    @Test
    fun `an iPhone starts its runner in the background once a team lets it take input`() = runBlocking {
        val iphone = iphone(ffmpegPath = null)
        iphone.screenSize()
        runners.refusal = "driving an iPhone needs your Apple development team"

        iphone.startRunnerInBackground()
        runners.refusal = null
        iphone.startRunnerInBackground()

        assertEquals(listOf("00008110"), runners.startedInBackground.map(XcTestRunnerTarget::udid))
    }

    @Test
    fun `an iPhone's input goes to the runner of that device`() = runBlocking {
        val iphone = iphone(ffmpegPath = null)

        iphone.pressButton(DeviceButton.VolumeUp)

        assertEquals(listOf("press VolumeUp"), runners.runner.calls)
        assertTrue(runners.askedFor.single() is XcTestRunnerTarget.Device)
    }

    @Test
    fun `an iPhone that takes input takes its screen size from its runner when its screen cannot be captured`() = runBlocking {
        helpersFailWith = IphoneCaptureExit.PermissionDenied

        assertEquals(IntSize(1206, 2622), iphone(ffmpegPath = null).screenSize())
        assertTrue(runners.askedFor.single() is XcTestRunnerTarget.Device)
    }

    @Test
    fun `an iPhone that takes no input and cannot be captured says why its screen cannot be captured`() = runBlocking {
        helpersFailWith = IphoneCaptureExit.PermissionDenied
        runners.refusal = "driving an iPhone needs your Apple development team"

        val failure = assertFailsWith<DeviceControlException> { iphone(ffmpegPath = null).screenSize() }

        assertContains(failure.message.orEmpty(), "Camera")
        assertTrue(runners.askedFor.isEmpty())
    }

    @Test
    fun `an iPhone whose screen cannot be captured still takes input through its runner`() = runBlocking {
        helpersFailWith = IphoneCaptureExit.PermissionDenied

        iphone(ffmpegPath = null).tap(x = 30, y = 60)

        assertEquals(listOf("tap 10.0,20.0"), runners.runner.calls)
    }

    @Test
    fun `an iPhone whose runner cannot start says why`() = runBlocking {
        runners.startFailure = "Developer Mode is off on the iPhone"

        val failure = assertFailsWith<DeviceControlException> { iphone(ffmpegPath = null).inputText("hi") }

        assertEquals("Developer Mode is off on the iPhone", failure.message)
    }

    private fun simulator() = IosSimulatorDeviceController(udid = "SIM-1", iosMajorVersion = 26, xcrunPath = fakeXcrun.path, runnerInput = XcTestRunnerInput(runners))

    @Test
    fun `an iPhone's input waits for its screen to be captured before it reaches the runner`() = runBlocking {
        helpersReportFrames = false
        val iphone = iphone(ffmpegPath = null)

        val tap = async(Dispatchers.IO) { iphone.tap(x = 30, y = 60) }
        val helper = withTimeout(TEST_TIMEOUT) { firstHelper.await() }
        helper.report("""{"event":"started","matchedBy":"usbSerialNumber","name":"Test iPhone","uniqueId":"capture-1"}""")
        assertTrue(runners.askedFor.isEmpty())

        helper.reportCapturing(IntSize(1206, 2622))
        withTimeout(TEST_TIMEOUT) { tap.await() }

        assertEquals(listOf("tap 10.0,20.0"), runners.runner.calls)
    }

    @Test
    fun `an iPhone's live view starts the runner once the first frame has arrived, and not before`() = runBlocking {
        helpersReportFrames = false
        val iphone = iphone(ffmpegPath = "ffmpeg")

        val opening = async(Dispatchers.IO) { iphone.openVideoStream(wanted = null) }
        val helper = withTimeout(TEST_TIMEOUT) { firstHelper.await() }
        helper.report("""{"event":"started","matchedBy":"usbSerialNumber","name":"Test iPhone","uniqueId":"capture-1"}""")
        val stream = withTimeout(TEST_TIMEOUT) { opening.await() }
        assertTrue(runners.startedInBackground.isEmpty())

        helper.reportCapturing(IntSize(1206, 2622))
        val started = withTimeout(TEST_TIMEOUT) { runners.firstStartedInBackground.await() }
        stream.close()

        assertEquals("00008110", started.udid)
    }

    private fun iphone(ffmpegPath: String?) = IosPhysicalDeviceController(
        udid = "00008110",
        name = "Test iPhone",
        iosMajorVersion = 26,
        captures = IphoneScreenCaptures(
            launcher = { command ->
                FakeCaptureHelperProcess(command) {}.also { helper ->
                    helpersFailWith?.let { failure ->
                        helper.report("""{"event":"error","reason":"${failure.name}","message":"the capture failed in a test"}""")
                        helper.exit(failure.code)
                    }
                    if (helpersReportFrames && helpersFailWith == null) helper.reportCapturing(IntSize(1206, 2622))
                    helpers += helper
                    firstHelper.complete(helper)
                }
            },
            idleTimeout = 1.minutes,
            failureReuse = 1.minutes,
            timeSource = TimeSource.Monotonic,
            scope = captureScope,
            helperExecutable = CompletableDeferred(File("jetwhale-iphone-capture")),
        ),
        ffmpegPath = ffmpegPath,
        runnerInput = XcTestRunnerInput(runners),
    )
}

/** Runners that hand out [runner] for every target, or refuse as told. */
private class FakeXcTestRunners : XcTestRunners {
    val runner = FakeXcTestRunner()
    val askedFor = mutableListOf<XcTestRunnerTarget>()
    val startedInBackground = mutableListOf<XcTestRunnerTarget>()
    val firstStartedInBackground = CompletableDeferred<XcTestRunnerTarget>()
    var refusal: String? = null
    var startFailure: String? = null
    var keptAliveUntil: Instant? = null

    override fun refusalFor(target: XcTestRunnerTarget): String? = refusal

    override suspend fun runnerFor(target: XcTestRunnerTarget): XcTestRunner {
        askedFor += target
        (refusal ?: startFailure)?.let { throw XcTestRunnerStartException(it, null) }
        return runner
    }

    override fun startRunnerInBackground(target: XcTestRunnerTarget) {
        startedInBackground += target
        firstStartedInBackground.complete(target)
    }

    override fun keptAliveUntil(target: XcTestRunnerTarget): Instant? = keptAliveUntil
}

/** A runner on a 1206x2622-pixel screen at 3x that notes what it is told, or refuses with [refusal]. */
private class FakeXcTestRunner : XcTestRunner {
    val calls = mutableListOf<String>()
    var refusal: String? = null
    var interfaceScreen = XcTestRunnerInterfaceScreen(XcTestRunnerOrientation.Portrait, widthPixels = 1206, heightPixels = 2622)

    /** What [streamScreenAsJpeg] sends, whatever rate it is asked for. */
    var screenFrames: Flow<ByteArray> = emptyFlow()

    /** The rates the screen was asked to stream at. */
    val screenStreamRates = mutableListOf<Int>()

    override val screen = XcTestRunnerScreen(widthPixels = 1206, heightPixels = 2622, scale = 3.0)

    override suspend fun tap(x: Double, y: Double, space: XcTestRunnerPointSpace) = note("tap $x,$y${spaceSuffix(space)}")

    override suspend fun longPress(x: Double, y: Double, durationMillis: Int, space: XcTestRunnerPointSpace) = note("long press $x,$y for $durationMillis${spaceSuffix(space)}")

    override suspend fun swipe(fromX: Double, fromY: Double, toX: Double, toY: Double, durationMillis: Int, space: XcTestRunnerPointSpace) = note("swipe $fromX,$fromY -> $toX,$toY in $durationMillis${spaceSuffix(space)}")

    override suspend fun interfaceScreen(): XcTestRunnerInterfaceScreen = interfaceScreen

    override suspend fun typeText(text: String) = note("type $text")

    override suspend fun pressButton(button: XcTestRunnerButton) = note("press ${button.name}")

    override suspend fun openAppSwitcher() = note("open app switcher")

    override suspend fun activateApp(bundleId: String) = note("activate $bundleId")

    override fun streamScreenAsJpeg(maxFps: Int): Flow<ByteArray> {
        screenStreamRates += maxFps
        return screenFrames
    }

    override suspend fun keepAlive(duration: Duration): Instant {
        note("keep alive for $duration")
        return LEASE_END
    }

    private fun note(call: String) {
        refusal?.let { throw XcTestRunnerException(it, null) }
        calls += call
    }

    private fun spaceSuffix(space: XcTestRunnerPointSpace) = if (space == XcTestRunnerPointSpace.Screen) " on screen" else ""
}

private val LEASE_END: Instant = Instant.parse("2026-10-09T02:00:00Z")

private val TEST_TIMEOUT = 10.seconds
