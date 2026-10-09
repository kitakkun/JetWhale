package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

internal const val IPROXY_INSTALL_COMMAND = "brew install libimobiledevice"

internal const val IPROXY_MISSING_REFUSAL = "driving an iPhone needs iproxy, which forwards a port to it over USB: $IPROXY_INSTALL_COMMAND"

private const val JVM_EXITING_REFUSAL = "this process is exiting, so it starts no XCTest runner"

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
    private val clock: Clock,
    private val scope: CoroutineScope,
) : XcTestRunners {
    private class StartedRunnerProcesses(val xcodebuild: Process, val forwarder: Process?)

    private val attachments = ConcurrentHashMap<String, Attachment>()

    /** The processes this client started, by xcodebuild's pid, for [destroyStartedRunnerProcesses]. */
    private val startedRunnerProcesses = ConcurrentHashMap<Long, StartedRunnerProcesses>()

    /** Destinations whose last start failed; a background start leaves them alone until [runnerFor] tries again. */
    private val failedDestinations: MutableSet<RunnerDestination> = ConcurrentHashMap.newKeySet()

    /** Set once this process is exiting, from when no runner may start: none would be ended. */
    @Volatile
    private var isJvmExiting = false

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

    override fun keptAliveUntil(target: XcTestRunnerTarget): Instant? {
        val keptAliveUntil = runnerStateDirectory.readRunnerState(target.udid)?.keptAliveUntilEpochMillis?.let(Instant::ofEpochMilli) ?: return null
        return keptAliveUntil.takeIf { it.isAfter(clock.instant()) }
    }

    /**
     * Records that [attachment]'s runner is kept alive for [leaseDuration] from now, unless its record is
     * already another runner's, and returns the time the lease ends.
     */
    suspend fun recordLease(attachment: Attachment, leaseDuration: Duration): Instant {
        val until = clock.instant().plusMillis(leaseDuration.inWholeMilliseconds)
        runnerStateDirectory.withDeviceLock(attachment.destination.udid, lockTimeout) {
            val runnerState = runnerStateDirectory.readRunnerState(attachment.destination.udid)?.takeIf { it.pid == attachment.pid } ?: return@withDeviceLock
            runnerStateDirectory.writeRunnerState(attachment.destination.udid, runnerState.copy(keptAliveUntilEpochMillis = until.toEpochMilli()))
        }
        return until
    }

    /**
     * Kills what this process started, without waiting, and starts nothing from now on; for when it
     * exits. A screen stream that its runner's end breaks would otherwise start another.
     */
    fun destroyStartedRunnerProcesses() {
        isJvmExiting = true
        startedRunnerProcesses.values.forEach {
            it.xcodebuild.destroyForcibly()
            it.forwarder?.destroyForcibly()
        }
    }

    private fun destinationOf(target: XcTestRunnerTarget): RunnerDestination {
        refusalFor(target)?.let { throw XcTestRunnerStartException(it, null) }
        return when (target) {
            is XcTestRunnerTarget.Simulator -> RunnerDestination.Simulator(target.udid)
            is XcTestRunnerTarget.Device -> RunnerDestination.Device(target.udid, settings.developmentTeam ?: throw XcTestRunnerStartException(NO_DEVELOPMENT_TEAM_REFUSAL, null))
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
        } catch (e: IOException) {
            failedDestinations += destination
            throw XcTestRunnerStartException("the XCTest runner's state or builds could not be read or written: ${e.message}", e)
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
        // Only a runner that answered is shut down: a live recorded pid may by now belong to an
        // unrelated process.
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
        if (isJvmExiting) throw XcTestRunnerStartException(JVM_EXITING_REFUSAL, null)
        val xctestrun = builds.xctestrunFor(destination)
        val runnerPort = portSource.freePort()
        val token = UUID.randomUUID().toString()
        val forward = if (destination is RunnerDestination.Device) forwardToDevice(destination.udid, runnerPort) else null
        var launchedTest: LaunchedTest? = null
        try {
            launchedTest = launchRunnerTest(xctestrun, destination.udid, runnerPort, token)
            // Registered before isJvmExiting is read again: an exit that begins meanwhile either
            // finds this xcodebuild to kill or has set the flag this check sees.
            startedRunnerProcesses[launchedTest.xcodebuild.pid()] = StartedRunnerProcesses(launchedTest.xcodebuild, forward?.process)
            if (isJvmExiting) throw XcTestRunnerStartException(JVM_EXITING_REFUSAL, null)
            val connection = connector.connect(forward?.localPort ?: runnerPort, token)
            val status = awaitRunnerAnswer(connection, launchedTest, destination)
            recordStartedRunner(destination, launchedTest.xcodebuild, forward, RunnerState(status.protocolVersion, launchedTest.xcodebuild.pid(), forward?.localPort ?: runnerPort, token, destination.developmentTeam))
            return Attachment(destination, launchedTest.xcodebuild.pid(), connection, status)
        } catch (e: Throwable) {
            launchedTest?.xcodebuild?.let { startedRunnerProcesses.remove(it.pid()) }
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
        KeptOutput(process, KEPT_OUTPUT_LINES)
        return Forward(process, localPort)
    }

    private suspend fun launchRunnerTest(xctestrun: File, udid: String, runnerPort: Int, token: String): LaunchedTest {
        val xcodebuild = withContext(Dispatchers.IO) {
            processLauncher.start(
                listOf(
                    "/usr/bin/env",
                    // xcodebuild passes TEST_RUNNER_-prefixed variables to the test runner with the
                    // prefix removed.
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
        return LaunchedTest(xcodebuild, KeptOutput(xcodebuild, KEPT_OUTPUT_LINES))
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

        /** Far longer than a build takes: one still running after this is waiting on something that will not come. */
        private val BUILD_TIMEOUT = 5.minutes

        /** Long enough for another plugin to build and start a device's runner. */
        private val LOCK_TIMEOUT = 10.minutes

        /** How long a build another client made is kept after it was last used. */
        private val UNUSED_BUILD_LIFETIME = 7.days

        fun onThisMac(stateDirectory: File, xcrunPath: String, iproxyPath: String?, settings: XcTestRunnerSettings): XcTestRunners {
            val zip = checkNotNull(LocalXcTestRunners::class.java.getResourceAsStream(RUNNER_PROJECT_RESOURCE)) { "$RUNNER_PROJECT_RESOURCE is missing from the client's jar" }.use { it.readBytes() }
            // The runner answers a command only once it has run it, and waits up to 60 seconds for
            // each event.
            val httpClient = OkHttpClient.Builder().readTimeout(2, TimeUnit.MINUTES).build()
            return LocalXcTestRunners(
                runnerStateDirectory = RunnerStateDirectory(File(stateDirectory, "runners")),
                builds = RunnerBuilds(File(stateDirectory, "builds"), zip, xcrunPath, ProcessCommandOutputRunner(SystemProcessLauncher, BUILD_TIMEOUT), LOCK_TIMEOUT, UNUSED_BUILD_LIFETIME, Clock.systemUTC()),
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
                clock = Clock.systemUTC(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            ).also { runners -> Runtime.getRuntime().addShutdownHook(Thread(runners::destroyStartedRunnerProcesses)) }
        }
    }
}

/** The most frames a second the runner streams its screen at. */
private const val MAX_SCREEN_STREAM_FPS = 60

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
    @Volatile private var attachment: Attachment,
    private val runners: LocalXcTestRunners,
) : XcTestRunner {
    override val screen: XcTestRunnerScreen
        get() = attachment.status.let { XcTestRunnerScreen(widthPixels = it.screenWidthPixels, heightPixels = it.screenHeightPixels, scale = it.scale) }

    override suspend fun tap(x: Double, y: Double, space: XcTestRunnerPointSpace) {
        send(
            "/tap",
            buildJsonObject {
                put("x", x)
                put("y", y)
                put("space", space.wireName)
            },
        )
    }

    override suspend fun longPress(x: Double, y: Double, durationMillis: Int, space: XcTestRunnerPointSpace) {
        send(
            "/longPress",
            buildJsonObject {
                put("x", x)
                put("y", y)
                put("durationMillis", durationMillis)
                put("space", space.wireName)
            },
        )
    }

    override suspend fun swipe(fromX: Double, fromY: Double, toX: Double, toY: Double, durationMillis: Int, space: XcTestRunnerPointSpace) {
        send(
            "/swipe",
            buildJsonObject {
                put("fromX", fromX)
                put("fromY", fromY)
                put("toX", toX)
                put("toY", toY)
                put("durationMillis", durationMillis)
                put("space", space.wireName)
            },
        )
    }

    override suspend fun interfaceScreen(): XcTestRunnerInterfaceScreen {
        val answer = send("/interfaceScreen", JsonObject(emptyMap()))
        val orientationName = answer.stringField("orientation")
        return XcTestRunnerInterfaceScreen(
            orientation = XcTestRunnerOrientation.entries.firstOrNull { it.wireName == orientationName } ?: throw XcTestRunnerException("the XCTest runner reported an unknown orientation: $orientationName", null),
            widthPixels = answer.intField("widthPixels"),
            heightPixels = answer.intField("heightPixels"),
        )
    }

    override suspend fun typeText(text: String) {
        var start = 0
        while (start < text.length) {
            var end = minOf(start + MAX_TYPED_CHARS_PER_COMMAND, text.length)
            // UTF-8 encoding turns each half of a split surrogate pair into '?'.
            if (end < text.length && text[end - 1].isHighSurrogate()) end--
            send("/typeText", buildJsonObject { put("text", text.substring(start, end)) })
            start = end
        }
    }

    override suspend fun pressButton(button: XcTestRunnerButton) {
        send("/pressButton", buildJsonObject { put("button", button.wireName) })
    }

    override suspend fun openAppSwitcher() {
        send("/openAppSwitcher", JsonObject(emptyMap()))
    }

    override suspend fun activateApp(bundleId: String) {
        send("/activateApp", buildJsonObject { put("bundleId", bundleId) })
    }

    override fun streamScreenAsJpeg(maxFps: Int): Flow<ByteArray> {
        require(maxFps in 1..MAX_SCREEN_STREAM_FPS) { "maxFps must be 1 to $MAX_SCREEN_STREAM_FPS, not $maxFps" }
        return channelFlow {
            var attachedSinceLastFrame = false
            while (true) {
                val sentFrameCount = try {
                    sendScreenFrames(attachment, maxFps)
                } catch (_: RunnerUnreachableException) {
                    0
                }
                if (sentFrameCount > 0) attachedSinceLastFrame = false
                if (attachedSinceLastFrame) throw XcTestRunnerException("the XCTest runner's screen stream ended before its first frame", null)
                attachment = runners.reattach(attachment)
                attachedSinceLastFrame = true
            }
        }.conflate()
    }

    /** Sends the frames of [attachment]'s screen stream into this flow until it ends; returns how many. */
    private suspend fun ProducerScope<ByteArray>.sendScreenFrames(attachment: Attachment, maxFps: Int): Int {
        val stream = attachment.connection.openScreenStream(maxFps)
        return coroutineScope {
            val reading = async(Dispatchers.IO) {
                var sentFrameCount = 0
                while (true) {
                    val frame = try {
                        stream.readJpegFrame()
                    } catch (_: IOException) {
                        null
                    } ?: break
                    send(frame)
                    sentFrameCount++
                }
                sentFrameCount
            }
            // A read waits on the socket and ignores cancellation; only closing the stream ends it.
            try {
                reading.await()
            } finally {
                stream.close()
            }
        }
    }

    override suspend fun keepAlive(duration: Duration): Instant {
        val answer = send("/lease", buildJsonObject { put("seconds", duration.toDouble(DurationUnit.SECONDS)) })
        return runners.recordLease(attachment, answer.doubleField("leaseSeconds").seconds)
    }

    private suspend fun send(path: String, body: JsonObject): JsonObject {
        try {
            return attachment.connection.send(path, body)
        } catch (_: RunnerUnreachableException) {
            attachment = runners.reattach(attachment)
        }
        try {
            return attachment.connection.send(path, body)
        } catch (e: RunnerUnreachableException) {
            throw XcTestRunnerException(e.message.orEmpty(), e)
        }
    }
}

private fun JsonObject.stringField(name: String): String = (this[name] as? JsonPrimitive)?.contentOrNull ?: throw XcTestRunnerException("the XCTest runner's answer has no $name", null)

private fun JsonObject.intField(name: String): Int = doubleField(name).toInt()

private fun JsonObject.doubleField(name: String): Double = (this[name] as? JsonPrimitive)?.doubleOrNull ?: throw XcTestRunnerException("the XCTest runner's answer has no number $name", null)
