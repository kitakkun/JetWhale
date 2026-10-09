package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.io.File
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

internal const val IPROXY_INSTALL_COMMAND = "brew install libimobiledevice"

internal const val IPROXY_MISSING_REFUSAL = "driving an iPhone needs iproxy, which forwards a port to it over USB: $IPROXY_INSTALL_COMMAND"

internal const val NO_DEVELOPMENT_TEAM_REFUSAL = "driving an iPhone needs your Apple development team, which signs the XCTest runner that sends its input"

private const val RUNNER_POLL_MILLIS = 250L

/** How long a runner gets to end its test after `/shutdown` before it is ended for it. */
private const val SHUTDOWN_GRACE_MILLIS = 3_000L

/** A runner this client is talking to: where it answers, and the screen it reported. */
internal class Attachment(
    val destination: RunnerDestination,
    val pid: Long,
    val connection: RunnerConnection,
    val status: RunnerStatus,
)

/**
 * The runners on this Mac, started with `xcodebuild test-without-building` and shared with every
 * other plugin through [runnerStateDirectory]: a runner another plugin started is used as it is, unless it
 * speaks an older protocol than this client, or was signed for another team, in which case it is
 * replaced. Starting or replacing a device's runner holds that device's lock, so two plugins never
 * start two runners for one device.
 *
 * A runner ends its own test after [idleTimeout] without commands, so it never outlives the hosts
 * that use it for long; the ones this process started are also ended when it exits.
 */
internal class LocalXcTestRunners(
    private val runnerStateDirectory: RunnerStateDirectory,
    private val builds: RunnerBuilds,
    private val xcrunPath: String,
    private val iproxyPath: String?,
    private val settings: XcTestRunnerSettings,
    private val processLauncher: ProcessLauncher,
    private val portSource: PortSource,
    private val connector: RunnerConnector,
    private val processTable: ProcessTable,
    private val idleTimeout: Duration,
    private val startTimeout: Duration,
    private val lockTimeout: Duration,
    private val scope: CoroutineScope,
) : XcTestRunners {
    private class StartedRunnerProcesses(val xcodebuild: Process, val forwarder: Process?)

    private val attachments = ConcurrentHashMap<String, Attachment>()

    /** The processes this client started, by xcodebuild's pid, for [destroyStartedRunnerProcesses]. */
    private val startedRunnerProcesses = ConcurrentHashMap<Long, StartedRunnerProcesses>()

    /** Destinations whose last start failed; a background start leaves them alone until [runnerFor] tries again. */
    private val failedDestinations: MutableSet<RunnerDestination> = ConcurrentHashMap.newKeySet()

    override fun refusalFor(target: XcTestRunnerTarget): String? {
        val iosMajorVersion = target.iosMajorVersion
        if (iosMajorVersion != null && iosMajorVersion < RUNNER_MINIMUM_IOS_MAJOR_VERSION) return "the XCTest runner needs iOS $RUNNER_MINIMUM_IOS_MAJOR_VERSION or later, and this one runs iOS $iosMajorVersion"
        return when (target) {
            is XcTestRunnerTarget.Simulator -> null

            is XcTestRunnerTarget.Device -> when {
                iproxyPath == null -> IPROXY_MISSING_REFUSAL
                settings.developmentTeam == null -> NO_DEVELOPMENT_TEAM_REFUSAL
                else -> null
            }
        }
    }

    override suspend fun runnerFor(target: XcTestRunnerTarget): XcTestRunner {
        val destination = destinationOf(target)
        return AttachedXcTestRunner(attach(destination), this)
    }

    override fun startRunnerInBackground(target: XcTestRunnerTarget) {
        if (refusalFor(target) != null) return
        val destination = destinationOf(target)
        if (destination in failedDestinations) return
        scope.launch {
            try {
                attach(destination)
            } catch (_: XcTestRunnerException) {
                // Nothing waits on a background start; the next runnerFor tries again and reports why
                // it fails.
            }
        }
    }

    /** Kills what this process started, without waiting; for when it exits. */
    fun destroyStartedRunnerProcesses() {
        startedRunnerProcesses.values.forEach {
            it.xcodebuild.destroyForcibly()
            it.forwarder?.destroyForcibly()
        }
    }

    private fun destinationOf(target: XcTestRunnerTarget): RunnerDestination {
        refusalFor(target)?.let { throw XcTestRunnerStartException(it, null) }
        return when (target) {
            is XcTestRunnerTarget.Simulator -> RunnerDestination.Simulator(target.udid)
            is XcTestRunnerTarget.Device -> RunnerDestination.Device(target.udid, checkNotNull(settings.developmentTeam))
        }
    }

    /** The runner in place of [gone], which stopped answering: another plugin's newer one, or a new one. */
    suspend fun reattach(gone: Attachment): Attachment {
        attachments.remove(gone.destination.udid, gone)
        return attach(gone.destination)
    }

    /** The runner for [destination]: the one this client knows, the one another plugin recorded, or a new one. */
    private suspend fun attach(destination: RunnerDestination): Attachment {
        attachments[destination.udid]?.takeIf { it.destination == destination && processTable.isAlive(it.pid) }?.let { return it }
        val attachment = try {
            runnerStateDirectory.withDeviceLock(destination.udid, lockTimeout) { attachToRecordedRunner(destination) ?: startRunner(destination) }
        } catch (e: XcTestRunnerStartException) {
            failedDestinations += destination
            throw e
        }
        failedDestinations -= destination
        attachments[destination.udid] = attachment
        return attachment
    }

    /**
     * The recorded runner of [destination]'s device, when it answers, speaks this client's protocol
     * or a newer one, and was started for the same team. A recorded runner that falls short is
     * shut down and forgotten.
     */
    private suspend fun attachToRecordedRunner(destination: RunnerDestination): Attachment? {
        val runnerState = runnerStateDirectory.readRunnerState(destination.udid) ?: return null
        val connection = connector.connect(runnerState.port, runnerState.token)
        val status = if (processTable.isAlive(runnerState.pid)) answeredStatus(connection) else null
        if (status != null && status.protocolVersion >= PROTOCOL_VERSION && runnerState.developmentTeam == destination.developmentTeam) {
            return Attachment(destination, runnerState.pid, connection, status)
        }
        // Only a runner that answered is shut down: a recorded pid that is alive may by now belong
        // to an unrelated process.
        if (status != null) shutDownRunner(connection, runnerState.pid)
        runnerStateDirectory.deleteRunnerState(destination.udid, runnerState.pid)
        return null
    }

    private suspend fun answeredStatus(connection: RunnerConnection): RunnerStatus? = try {
        connection.status()
    } catch (_: RunnerUnreachableException) {
        null
    } catch (_: XcTestRunnerException) {
        null
    }

    private suspend fun shutDownRunner(connection: RunnerConnection, pid: Long) {
        try {
            connection.send("/shutdown", JsonObject(emptyMap()))
        } catch (_: RunnerUnreachableException) {
        } catch (_: XcTestRunnerException) {
        }
        val exited = withTimeoutOrNull(SHUTDOWN_GRACE_MILLIS) {
            while (processTable.isAlive(pid)) delay(RUNNER_POLL_MILLIS)
            true
        }
        if (exited == null) processTable.terminate(pid)
    }

    private class Forward(val process: Process, val localPort: Int)

    private class LaunchedTest(val xcodebuild: Process, val output: KeptOutput)

    private suspend fun startRunner(destination: RunnerDestination): Attachment {
        val xctestrun = builds.xctestrunFor(destination)
        val runnerPort = portSource.freePort()
        val token = UUID.randomUUID().toString()
        val forward = if (destination is RunnerDestination.Device) forwardToDevice(destination.udid, runnerPort) else null
        var launchedTest: LaunchedTest? = null
        try {
            launchedTest = launchRunnerTest(xctestrun, destination.udid, runnerPort, token)
            val connection = connector.connect(forward?.localPort ?: runnerPort, token)
            val status = awaitRunnerAnswer(connection, launchedTest, destination)
            recordStartedRunner(destination, launchedTest.xcodebuild, forward, RunnerState(status.protocolVersion, launchedTest.xcodebuild.pid(), forward?.localPort ?: runnerPort, token, destination.developmentTeam))
            return Attachment(destination, launchedTest.xcodebuild.pid(), connection, status)
        } catch (e: Throwable) {
            launchedTest?.xcodebuild?.destroyForcibly()
            forward?.process?.destroyForcibly()
            throw e
        }
    }

    /** Forwards a free port here to [runnerPort] on the device, over usbmux. */
    private suspend fun forwardToDevice(udid: String, runnerPort: Int): Forward {
        val iproxy = iproxyPath ?: throw XcTestRunnerStartException(IPROXY_MISSING_REFUSAL, null)
        val localPort = portSource.freePort()
        val process = withContext(Dispatchers.IO) { processLauncher.start(listOf(iproxy, "$localPort:$runnerPort", "--udid", udid)) }
        // Drains iproxy's output, which would otherwise fill its pipes and stall it.
        KeptOutput(process)
        return Forward(process, localPort)
    }

    private suspend fun launchRunnerTest(xctestrun: File, udid: String, runnerPort: Int, token: String): LaunchedTest {
        val xcodebuild = withContext(Dispatchers.IO) {
            // xcodebuild passes TEST_RUNNER_-prefixed variables to the test runner with the prefix
            // removed.
            processLauncher.start(
                listOf(
                    "/usr/bin/env",
                    "TEST_RUNNER_JETWHALE_RUNNER_PORT=$runnerPort",
                    "TEST_RUNNER_JETWHALE_RUNNER_TOKEN=$token",
                    "TEST_RUNNER_JETWHALE_RUNNER_IDLE_SECONDS=${idleTimeout.inWholeSeconds}",
                    xcrunPath, "xcodebuild", "test-without-building",
                    "-xctestrun", xctestrun.path,
                    "-destination", "id=$udid",
                    "-only-testing:$RUNNER_TEST_IDENTIFIER",
                ),
            )
        }
        return LaunchedTest(xcodebuild, KeptOutput(xcodebuild))
    }

    /** The runner's status once it answers; why it did not, when its xcodebuild exits or the start times out. */
    private suspend fun awaitRunnerAnswer(connection: RunnerConnection, launchedTest: LaunchedTest, destination: RunnerDestination): RunnerStatus = withTimeoutOrNull(startTimeout) { awaitStatus(connection, launchedTest.xcodebuild) }
        ?: throw XcTestRunnerStartException(
            if (launchedTest.xcodebuild.isAlive) "the XCTest runner did not answer within $startTimeout" else XcodebuildFailures.reasonOf(launchedTest.output.textOnceDrained(), destination),
            null,
        )

    /** Records a runner this process started for other plugins, and forgets it once its test ends. */
    private fun recordStartedRunner(destination: RunnerDestination, xcodebuild: Process, forward: Forward?, runnerState: RunnerState) {
        runnerStateDirectory.writeRunnerState(destination.udid, runnerState)
        startedRunnerProcesses[runnerState.pid] = StartedRunnerProcesses(xcodebuild, forward?.process)
        xcodebuild.onExit().thenRun {
            forward?.process?.destroy()
            startedRunnerProcesses.remove(runnerState.pid)
            scope.launch { runnerStateDirectory.withDeviceLock(destination.udid, lockTimeout) { runnerStateDirectory.deleteRunnerState(destination.udid, runnerState.pid) } }
        }
    }

    /**
     * The runner's status once it answers, or null when its xcodebuild exits first. A runner that
     * answers without a status it can report did not start, as far as its callers are concerned.
     */
    private suspend fun awaitStatus(connection: RunnerConnection, xcodebuild: Process): RunnerStatus? {
        while (xcodebuild.isAlive) {
            try {
                return connection.status()
            } catch (_: RunnerUnreachableException) {
                delay(RUNNER_POLL_MILLIS)
            } catch (e: XcTestRunnerException) {
                throw XcTestRunnerStartException("the XCTest runner started but did not report its status: ${e.message}", e)
            }
        }
        return null
    }

    companion object {
        /** How long a runner waits for a command before it ends its test. */
        private val IDLE_TIMEOUT = 5.minutes

        /** A simulator's runner starts in seconds; a device's may first be signed and installed. */
        private val START_TIMEOUT = 3.minutes

        /** Long enough for another plugin to build and start a device's runner. */
        private val LOCK_TIMEOUT = 10.minutes

        /** How long a build another client made is kept after it was last used. */
        private val UNUSED_BUILD_LIFETIME = 7.days

        fun onThisMac(stateDirectory: File, xcrunPath: String, iproxyPath: String?, settings: XcTestRunnerSettings): XcTestRunners {
            val zip = checkNotNull(LocalXcTestRunners::class.java.getResourceAsStream(RUNNER_PROJECT_RESOURCE)) { "$RUNNER_PROJECT_RESOURCE is missing from the client's jar" }.use { it.readBytes() }
            // The runner answers a command only once it has run, and waits up to 60 seconds for
            // each event.
            val httpClient = OkHttpClient.Builder().readTimeout(2, TimeUnit.MINUTES).build()
            return LocalXcTestRunners(
                runnerStateDirectory = RunnerStateDirectory(File(stateDirectory, "runners")),
                builds = RunnerBuilds(File(stateDirectory, "builds"), zip, xcrunPath, SystemCommandOutputRunner, LOCK_TIMEOUT, UNUSED_BUILD_LIFETIME, Clock.systemUTC()),
                xcrunPath = xcrunPath,
                iproxyPath = iproxyPath,
                settings = settings,
                processLauncher = SystemProcessLauncher,
                portSource = LocalPorts,
                connector = { port, token -> HttpRunnerConnection(port, token, httpClient) },
                processTable = SystemProcessTable,
                idleTimeout = IDLE_TIMEOUT,
                startTimeout = START_TIMEOUT,
                lockTimeout = LOCK_TIMEOUT,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            ).also { runners -> Runtime.getRuntime().addShutdownHook(Thread(runners::destroyStartedRunnerProcesses)) }
        }
    }
}

/**
 * The most characters one `/typeText` carries; a longer text goes in parts. The runner types 60
 * characters a second and gives an event 60 seconds, so a part takes about 17 seconds.
 */
private const val MAX_TYPED_CHARS_PER_COMMAND = 1_000

/** The runner's Xcode project, zipped into this client's resources by its build. */
private const val RUNNER_PROJECT_RESOURCE = "/com/kitakkun/jetwhale/plugins/xctestrunner/JetWhaleRunner.zip"

/**
 * The [XcTestRunner] callers hold: commands go to the current [attachment], and a runner that stopped
 * answering is replaced through [runners] and the command sent once more.
 */
private class AttachedXcTestRunner(
    private var attachment: Attachment,
    private val runners: LocalXcTestRunners,
) : XcTestRunner {
    override val screen: XcTestRunnerScreen
        get() = attachment.status.let { XcTestRunnerScreen(widthPixels = it.screenWidthPixels, heightPixels = it.screenHeightPixels, scale = it.scale) }

    override suspend fun tap(x: Double, y: Double) = send(
        "/tap",
        buildJsonObject {
            put("x", x)
            put("y", y)
        },
    )

    override suspend fun longPress(x: Double, y: Double, durationMillis: Int) = send(
        "/longPress",
        buildJsonObject {
            put("x", x)
            put("y", y)
            put("durationMillis", durationMillis)
        },
    )

    override suspend fun swipe(fromX: Double, fromY: Double, toX: Double, toY: Double, durationMillis: Int) = send(
        "/swipe",
        buildJsonObject {
            put("fromX", fromX)
            put("fromY", fromY)
            put("toX", toX)
            put("toY", toY)
            put("durationMillis", durationMillis)
        },
    )

    override suspend fun typeText(text: String) {
        var start = 0
        while (start < text.length) {
            var end = minOf(start + MAX_TYPED_CHARS_PER_COMMAND, text.length)
            // UTF-8 encodes each half of a split surrogate pair as '?'.
            if (end < text.length && text[end - 1].isHighSurrogate()) end--
            send("/typeText", buildJsonObject { put("text", text.substring(start, end)) })
            start = end
        }
    }

    override suspend fun pressButton(button: XcTestRunnerButton) = send("/pressButton", buildJsonObject { put("button", button.wireName) })

    override suspend fun openAppSwitcher() = send("/openAppSwitcher", JsonObject(emptyMap()))

    override suspend fun activateApp(bundleId: String) = send("/activateApp", buildJsonObject { put("bundleId", bundleId) })

    private suspend fun send(path: String, body: JsonObject) {
        try {
            attachment.connection.send(path, body)
            return
        } catch (_: RunnerUnreachableException) {
            attachment = runners.reattach(attachment)
        }
        try {
            attachment.connection.send(path, body)
        } catch (e: RunnerUnreachableException) {
            throw XcTestRunnerException(e.message.orEmpty(), e)
        }
    }
}
