package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.zip.ZipInputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class LocalXcTestRunnersTest {
    private val stateRoot: File = Files.createTempDirectory("xctest-runners").toFile()
    private val stateDirectory = RunnerStateDirectory(File(stateRoot, "runners"))
    private val xcodebuildCommands = FakeXcodebuildCommands()
    private val launched = mutableListOf<FakeToolProcess>()
    private val connections = mutableMapOf<Int, FakeRunnerConnection>()
    private val settings = TeamSettings()
    private val clock = MutableClock(Instant.parse("2026-10-09T00:00:00Z"))
    private var nextPort = 20_000
    private var nextPid = 500L

    /** What a launched xcodebuild prints, whether it exits right after, and how its runner answers. */
    private var xcodebuildOutput = "Testing started\n"
    private var xcodebuildExitsAtOnce = false
    private var runnersAnswer = true

    private val simulator = XcTestRunnerTarget.Simulator("SIM-1", iosMajorVersion = 26)
    private val device = XcTestRunnerTarget.Device("00008110", iosMajorVersion = 26)

    private val xcodebuildProcesses: List<FakeToolProcess> get() = launched.filter { it.command.first() == "/usr/bin/env" }

    @AfterTest
    fun deleteStateRoot() {
        stateRoot.deleteRecursively()
    }

    @Test
    fun `the first plugin to ask builds and starts the device's runner and records it for the others`() = runTest {
        runners().runnerFor(simulator)

        assertEquals(1, xcodebuildCommands.buildCommands.size)
        val command = xcodebuildProcesses.single().command
        assertEquals(listOf("/usr/bin/env", "TEST_RUNNER_JETWHALE_RUNNER_PORT=20000"), command.take(2))
        val token = command[2].removePrefix("TEST_RUNNER_JETWHALE_RUNNER_TOKEN=")
        assertEquals("TEST_RUNNER_JETWHALE_RUNNER_IDLE_SECONDS=300", command[3])
        assertEquals(listOf("xcrun", "xcodebuild", "test-without-building", "-xctestrun"), command.subList(4, 8))
        assertEquals(listOf("-destination", "id=SIM-1", "-only-testing:$RUNNER_TEST_IDENTIFIER"), command.takeLast(3))
        assertEquals(RunnerState(protocolVersion = PROTOCOL_VERSION, pid = 500, port = 20_000, token = token, developmentTeam = null), stateDirectory.readRunnerState("SIM-1"))
    }

    @Test
    fun `another plugin uses the recorded runner rather than starting a second one`() = runTest {
        runners().runnerFor(simulator)

        runners().runnerFor(simulator).tap(x = 201.0, y = 437.0, space = XcTestRunnerPointSpace.Device)

        assertEquals(1, xcodebuildProcesses.size)
        assertEquals(listOf("/tap"), connections.getValue(20_000).paths)
    }

    @Test
    fun `a recorded runner older than this client is shut down and replaced`() = runTest {
        recordRunner(protocolVersion = PROTOCOL_VERSION - 1, developmentTeam = null)

        runners().runnerFor(simulator)

        assertEquals(listOf("/shutdown"), connections.getValue(19_000).paths)
        assertEquals(1, xcodebuildProcesses.size)
        assertEquals(PROTOCOL_VERSION, stateDirectory.readRunnerState("SIM-1")?.protocolVersion)
    }

    @Test
    fun `a recorded runner newer than this client serves it as it is`() = runTest {
        recordRunner(protocolVersion = PROTOCOL_VERSION + 1, developmentTeam = null)

        runners().runnerFor(simulator).typeText("hi")

        assertTrue(xcodebuildProcesses.isEmpty())
        assertEquals(listOf("/typeText"), connections.getValue(19_000).paths)
    }

    @Test
    fun `a recorded runner that no longer answers is forgotten and a new one started, with nothing killed`() = runTest {
        recordRunner(protocolVersion = PROTOCOL_VERSION, developmentTeam = null)
        connections.getValue(19_000).answering = false

        runners().runnerFor(simulator)

        assertTrue(connections.getValue(19_000).paths.isEmpty())
        assertFalse(launched.first().destroyed)
        assertEquals(1, xcodebuildProcesses.size)
    }

    @Test
    fun `a device's recorded runner signed for another team is replaced`() = runTest {
        settings.developmentTeam = "ABCDE12345"
        recordRunner(protocolVersion = PROTOCOL_VERSION, developmentTeam = "VWXYZ67890", udid = device.udid)

        runners(iproxyPath = "iproxy").runnerFor(device)

        assertEquals(listOf("/shutdown"), connections.getValue(19_000).paths)
        assertEquals("ABCDE12345", stateDirectory.readRunnerState(device.udid)?.developmentTeam)
    }

    @Test
    fun `a runner whose test ended is replaced on the next command, which goes to the new one`() = runTest {
        val runner = runners().runnerFor(simulator)
        xcodebuildProcesses.single().exit()
        connections.getValue(20_000).unreachable = true

        runner.tap(x = 1.0, y = 2.0, space = XcTestRunnerPointSpace.Device)

        assertEquals(2, xcodebuildProcesses.size)
        val (path, body) = connections.getValue(20_001).sent.single()
        assertEquals("/tap", path)
        assertEquals(2.0, body.getValue("y").jsonPrimitive.double)
    }

    @Test
    fun `a point is sent with the space it is in`() = runTest {
        val runner = runners().runnerFor(simulator)

        runner.tap(x = 300.0, y = 100.0, space = XcTestRunnerPointSpace.Screen)
        runner.swipe(fromX = 1.0, fromY = 2.0, toX = 3.0, toY = 4.0, durationMillis = 250, space = XcTestRunnerPointSpace.Device)

        assertEquals(listOf("screen", "device"), connections.getValue(20_000).sent.map { (_, body) -> body.getValue("space").jsonPrimitive.content })
    }

    @Test
    fun `the interface screen is how the runner reports it`() = runTest {
        val runner = runners().runnerFor(simulator)
        connections.getValue(20_000).answers["/interfaceScreen"] = buildJsonObject {
            put("orientation", "landscapeRight")
            put("widthPixels", 2622)
            put("heightPixels", 1206)
        }

        val screen = runner.interfaceScreen()

        assertEquals(XcTestRunnerOrientation.LandscapeRight, screen.orientation)
        assertEquals(2622 to 1206, screen.widthPixels to screen.heightPixels)
    }

    @Test
    fun `a lease the runner grants is recorded for every client until it ends`() = runTest {
        val runner = runners().runnerFor(simulator)
        connections.getValue(20_000).answers["/lease"] = buildJsonObject { put("leaseSeconds", 600) }

        val until = runner.keepAlive(10.minutes)

        assertEquals(600, connections.getValue(20_000).sent.last().second.getValue("seconds").jsonPrimitive.int)
        assertEquals(Instant.parse("2026-10-09T00:10:00Z"), until)
        assertEquals(until, runners().keptAliveUntil(simulator))
        clock.now = Instant.parse("2026-10-09T00:10:01Z")
        assertNull(runners().keptAliveUntil(simulator))
    }

    @Test
    fun `a command the runner stopped answering after taking is not sent again`() = runTest {
        val runner = runners().runnerFor(simulator)
        connections.getValue(20_000).stopsAnsweringMidCommand = true

        assertFailsWith<XcTestRunnerException> { runner.tap(x = 1.0, y = 2.0, space = XcTestRunnerPointSpace.Device) }

        assertEquals(1, xcodebuildProcesses.size)
        assertEquals(listOf("/tap"), connections.getValue(20_000).paths)
    }

    @Test
    fun `a runner whose xcodebuild exits before it answers is explained from what xcodebuild printed`() = runTest {
        xcodebuildOutput = "xcodebuild: error: Unable to find a destination matching the provided destination specifier\n"
        xcodebuildExitsAtOnce = true
        runnersAnswer = false

        val failure = assertFailsWith<XcTestRunnerStartException> { runners().runnerFor(simulator) }

        assertEquals("the XCTest runner did not start: xcodebuild: error: Unable to find a destination matching the provided destination specifier", failure.message)
        assertNull(stateDirectory.readRunnerState("SIM-1"))
    }

    @Test
    fun `a runner that never answers is reported after the start timeout and its xcodebuild ended`() = runTest {
        runnersAnswer = false

        val failure = assertFailsWith<XcTestRunnerStartException> { runners().runnerFor(simulator) }

        assertEquals("the XCTest runner did not answer within 2m", failure.message)
        assertTrue(xcodebuildProcesses.single().destroyed)
    }

    @Test
    fun `a new runner that answers without its status did not start, and its xcodebuild is ended`() = runTest {
        connections[20_000] = FakeRunnerConnection(answering = true).apply { statusFailure = "the XCTest runner failed to status: no screenshot" }

        val failure = assertFailsWith<XcTestRunnerStartException> { runners().runnerFor(simulator) }

        assertEquals("the XCTest runner started but did not report its status: the XCTest runner failed to status: no screenshot", failure.message)
        assertTrue(xcodebuildProcesses.single().destroyed)
    }

    @Test
    fun `a device is refused before anything starts without iproxy or a development team`() = runTest {
        settings.developmentTeam = "ABCDE12345"
        assertEquals(IPROXY_MISSING_REFUSAL, runners(iproxyPath = null).refusalFor(device))
        settings.developmentTeam = null
        assertEquals(NO_DEVELOPMENT_TEAM_REFUSAL, runners(iproxyPath = "iproxy").refusalFor(device))

        val failure = assertFailsWith<XcTestRunnerStartException> { runners(iproxyPath = "iproxy").runnerFor(device) }

        assertEquals(NO_DEVELOPMENT_TEAM_REFUSAL, failure.message)
        assertTrue(launched.isEmpty())
        assertNull(runners(iproxyPath = null).refusalFor(simulator))
    }

    @Test
    fun `an iOS older than the runner's deployment target is refused before anything is built, and an unknown one is tried`() = runTest {
        settings.developmentTeam = "ABCDE12345"
        val runners = runners(iproxyPath = "iproxy")

        assertEquals("the XCTest runner needs iOS 17 or later, and this one runs iOS 16", runners.refusalFor(XcTestRunnerTarget.Simulator("SIM-16", iosMajorVersion = 16)))
        assertEquals("the XCTest runner needs iOS 17 or later, and this one runs iOS 16", runners.refusalFor(XcTestRunnerTarget.Device("00008020", iosMajorVersion = 16)))
        assertFailsWith<XcTestRunnerStartException> { runners.runnerFor(XcTestRunnerTarget.Simulator("SIM-16", iosMajorVersion = 16)) }
        assertTrue(launched.isEmpty())
        assertNull(runners.refusalFor(XcTestRunnerTarget.Simulator("SIM-17", iosMajorVersion = 17)))
        assertNull(runners.refusalFor(XcTestRunnerTarget.Simulator("SIM-X", iosMajorVersion = null)))
    }

    @Test
    fun `the oldest iOS the client accepts is the runner project's deployment target`() {
        val runnerProjectZip = checkNotNull(LocalXcTestRunners::class.java.getResourceAsStream("/com/kitakkun/jetwhale/plugins/xctestrunner/JetWhaleRunner.zip"))
        val pbxproj = ZipInputStream(runnerProjectZip).use { zipInput ->
            generateSequence { zipInput.nextEntry }.first { it.name == "JetWhaleRunner.xcodeproj/project.pbxproj" }
            zipInput.readBytes().decodeToString()
        }

        val deploymentTargetMajorVersions = Regex("""IPHONEOS_DEPLOYMENT_TARGET = (\d+)\.\d+;""").findAll(pbxproj).map { it.groupValues[1].toInt() }.toSet()

        assertEquals(setOf(RUNNER_MINIMUM_IOS_MAJOR_VERSION), deploymentTargetMajorVersions)
    }

    @Test
    fun `a device's runner is signed for the team and reached through iproxy`() = runTest {
        settings.developmentTeam = "ABCDE12345"

        runners(iproxyPath = "iproxy").runnerFor(device).pressButton(XcTestRunnerButton.VolumeUp)

        assertTrue("DEVELOPMENT_TEAM=ABCDE12345" in xcodebuildCommands.buildCommands.single())
        assertEquals(listOf("iproxy", "20001:20000", "--udid", "00008110"), launched.first().command)
        assertEquals(listOf("/pressButton"), connections.getValue(20_001).paths)
    }

    @Test
    fun `when a runner's xcodebuild exits, its record goes and its forward is stopped`() = runTest {
        settings.developmentTeam = "ABCDE12345"
        runners(iproxyPath = "iproxy").runnerFor(device)

        xcodebuildProcesses.single().exit()
        delay(1.seconds)

        assertNull(stateDirectory.readRunnerState(device.udid))
        assertTrue(launched.first { it.command.first() == "iproxy" }.destroyed)
    }

    @Test
    fun `a start that failed is not tried again in the background, only when a plugin asks`() = runTest {
        xcodebuildCommands.buildFailure = "error: something broke"
        val runners = runners()
        assertFailsWith<XcTestRunnerStartException> { runners.runnerFor(simulator) }

        runners.startRunnerInBackground(simulator)
        delay(1.minutes)
        assertEquals(1, xcodebuildCommands.buildCommands.size)

        xcodebuildCommands.buildFailure = null
        runners.runnerFor(simulator)
        assertEquals(2, xcodebuildCommands.buildCommands.size)
    }

    @Test
    fun `a process that exits ends the runners it started`() = runTest {
        val runners = runners()
        runners.runnerFor(simulator)

        runners.destroyStartedRunnerProcesses()

        assertTrue(xcodebuildProcesses.single().destroyed)
    }

    /** Records a runner some other plugin started, answering on port 19000 with [protocolVersion]. */
    private fun recordRunner(protocolVersion: Int, developmentTeam: String?, udid: String = simulator.udid) {
        val process = FakeToolProcess(listOf("xcodebuild started by another plugin"), pid = 400, output = "", exitsAtOnce = false).also(launched::add)
        connections[19_000] = FakeRunnerConnection(answering = true, protocolVersion = protocolVersion)
        stateDirectory.writeRunnerState(udid, RunnerState(protocolVersion, process.pid(), 19_000, "old-token", developmentTeam))
        connections.getValue(19_000).onShutdown = process::exit
    }

    private fun TestScope.runners(iproxyPath: String? = null) = LocalXcTestRunners(
        runnerStateDirectory = stateDirectory,
        builds = RunnerBuilds(File(stateRoot, "builds"), runnerProjectZip(), "xcrun", xcodebuildCommands, lockTimeout = 1.minutes, unusedBuildLifetime = 7.days, clock = Clock.systemUTC()),
        xcrunPath = "xcrun",
        iproxyPath = iproxyPath,
        settings = settings,
        processLauncher = { command ->
            val isXcodebuild = command.first() == "/usr/bin/env"
            FakeToolProcess(command, pid = nextPid++, output = if (isXcodebuild) xcodebuildOutput else "", exitsAtOnce = isXcodebuild && xcodebuildExitsAtOnce).also(launched::add)
        },
        portSource = { nextPort++ },
        connector = { port, _ -> connections.getOrPut(port) { FakeRunnerConnection(answering = runnersAnswer) } },
        processTable = object : ProcessTable {
            override fun isAlive(pid: Long): Boolean = launched.any { it.pid() == pid && it.isAlive }

            override fun terminate(pid: Long) {
                launched.filter { it.pid() == pid }.forEach(FakeToolProcess::destroy)
            }
        },
        idleTimeout = 5.minutes,
        startTimeout = 2.minutes,
        lockTimeout = 1.minutes,
        clock = clock,
        scope = backgroundScope,
    )
}

private class TeamSettings : XcTestRunnerSettings {
    override var developmentTeam: String? = null
}

/** A clock the test moves by hand. */
private class MutableClock(var now: Instant) : Clock() {
    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this
}
