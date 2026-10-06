package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HeldLock
import com.kitakkun.jetwhale.host.release.HostDirectory
import com.kitakkun.jetwhale.host.release.HostPlatformRelease
import com.kitakkun.jetwhale.host.release.HostReleaseMetadata
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataReader
import com.kitakkun.jetwhale.host.release.HostRuntimeRequirements
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.InstanceJson
import com.kitakkun.jetwhale.host.release.LauncherCapabilities
import com.kitakkun.jetwhale.host.release.LockFiles
import com.kitakkun.jetwhale.host.release.ReleaseMetadataSignatureVerifier
import com.kitakkun.jetwhale.host.release.hostJarName
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

const val PLATFORM = "macos-arm64"

private const val LOCK_WAIT_LIMIT_SECONDS = 10L

val capableLauncher = LauncherCapabilities(
    contract = 1,
    javaFeatureVersion = 21,
    modules = setOf("java.base", "java.desktop"),
    platformKey = PLATFORM,
    jvmArguments = listOf("-Dcompose.application.configure.swing.globals=true"),
)

fun hostVersion(name: String): HostVersion = checkNotNull(HostVersion.parse(name)) { name }

fun hostMetadata(versionName: String, jarBytes: ByteArray) = HostReleaseMetadata(
    format = 1,
    version = hostVersion(versionName),
    mainClass = "com.kitakkun.jetwhale.host.MainKt",
    launcherContract = 1,
    runtime = HostRuntimeRequirements(javaFeatureVersion = 21, modules = listOf("java.base", "java.desktop")),
    jvmArgs = listOf("-Dcompose.application.configure.swing.globals=true"),
    platforms = mapOf(
        PLATFORM to HostPlatformRelease(
            url = "https://example.com/${hostJarName(hostVersion(versionName), PLATFORM)}",
            size = jarBytes.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(jarBytes).joinToString("") { "%02x".format(it) },
            jvmArgs = listOf("-Dapple.awt.application.appearance=system"),
        ),
    ),
)

/** Writes a version directory, or the bundled one, as the host's download or the package leaves it. */
fun writeHostVersion(
    directory: Path,
    versionName: String,
    edit: (HostReleaseMetadata) -> HostReleaseMetadata,
) {
    val jarBytes = "host $versionName".toByteArray()
    Files.createDirectories(directory)
    Files.write(directory.resolve(hostJarName(hostVersion(versionName), PLATFORM)), jarBytes)
    Files.writeString(directory.resolve("release.json"), edit(hostMetadata(versionName, jarBytes)).encode())
}

/**
 * OS file locks as separate processes see them, for launches that run as threads or one after
 * another. Each process's locks go when [end] says the process ended, as the OS releases them. A
 * wait for a lock fails after [LOCK_WAIT_LIMIT_SECONDS] rather than hang the test run.
 */
class FakeLockFiles {
    private val semaphores = ConcurrentHashMap<Path, Semaphore>()
    private val heldByProcess = ConcurrentHashMap<Long, MutableList<Semaphore>>()

    /** The locks as the process [pid] takes them. */
    fun of(pid: Long): LockFiles = object : LockFiles {
        override fun lock(path: Path): HeldLock {
            val semaphore = semaphoreOf(path)
            check(semaphore.tryAcquire(LOCK_WAIT_LIMIT_SECONDS, TimeUnit.SECONDS)) { "process $pid waited for ${path.fileName}, which nothing released" }
            return held(pid, semaphore)
        }

        override fun tryLock(path: Path): HeldLock? = semaphoreOf(path).takeIf(Semaphore::tryAcquire)?.let { held(pid, it) }
    }

    /** Releases every lock the process [pid] holds. */
    fun end(pid: Long) {
        heldByProcess.remove(pid)?.forEach(Semaphore::release)
    }

    fun isHeld(path: Path): Boolean = semaphoreOf(path).availablePermits() == 0

    private fun held(pid: Long, semaphore: Semaphore): HeldLock {
        val locks = heldByProcess.computeIfAbsent(pid) { CopyOnWriteArrayList() }
        locks += semaphore
        return HeldLock {
            if (locks.remove(semaphore)) semaphore.release()
        }
    }

    private fun semaphoreOf(path: Path): Semaphore = semaphores.computeIfAbsent(path) { Semaphore(1) }
}

/** Processes by ID: those in [runningPids] run, with the start time [startMillisOf] gives them. */
class FakeProcessTable(
    override val currentPid: Long,
    private val runningPids: Set<Long>,
    private val startMillisOf: (Long) -> Long?,
    private val onAwaitExit: (Long) -> Unit,
) : ProcessTable {
    override val currentStartMillis: Long? = startMillisOf(currentPid)

    override fun isRunning(pid: Long, startMillis: Long?): Boolean = pid in runningPids && (startMillis == null || startMillis == startMillisOf(pid))

    override fun awaitExit(pid: Long) = onAwaitExit(pid)
}

/**
 * Publication goes through the real `instance.json`, as a host's would; a request to come forward
 * only counts and answers.
 */
class FakeRunningHostChannel(
    private val hostDirectory: HostDirectory,
    private val answers: Boolean,
) : RunningHostChannel {
    var bringToFrontRequests = 0
        private set

    fun publishInstanceJson(pid: Long) {
        hostDirectory.publishInstanceJson(InstanceJson(port = 0, pid = pid, token = "token"))
    }

    override fun isInstanceJsonPublishedBy(pid: Long): Boolean = hostDirectory.readInstanceJson()?.pid == pid

    override fun requestBringToFront(): Boolean {
        bringToFrontRequests++
        return answers
    }
}

/**
 * An app data directory with a bundled `1.0.0-alpha13`, where each launch runs as a process of its
 * own: [launch] starts one, and the test then ends its host the way the case needs.
 */
class LaunchTestBed {
    val appData: Path = Files.createTempDirectory("launcher-test")
    val hostDirectory = HostDirectory(appData.resolve("host"))
    val bundledDirectory: Path = appData.resolve("package/host")
    val lockFiles = FakeLockFiles()
    val runningHostChannel = FakeRunningHostChannel(hostDirectory, answers = true)
    val logLines = CopyOnWriteArrayList<String>()

    /** The processes that run, other than those the test has ended. */
    private val runningPids: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    init {
        writeHostVersion(bundledDirectory, "1.0.0-alpha13") { it }
        Files.move(bundledDirectory.resolve(hostJarName(hostVersion("1.0.0-alpha13"), PLATFORM)), bundledDirectory.resolve(BundledHostDirectory.JAR_FILE_NAME))
    }

    fun download(versionName: String) {
        writeHostVersion(versionDirectory(versionName), versionName) { it }
    }

    fun versionDirectory(versionName: String): Path = hostDirectory.root.resolve(versionName)

    fun launcher(
        pid: Long,
        lockFiles: LockFiles = this.lockFiles.of(pid),
        metadataReader: HostReleaseMetadataReader = HostReleaseMetadataReader(ReleaseMetadataSignatureVerifier.JetWhaleReleases),
        runningHostChannel: RunningHostChannel = this.runningHostChannel,
        onAwaitExit: (Long) -> Unit = {},
        bundled: Boolean = true,
        sleep: (Duration) -> Unit = {},
    ): HostLauncher {
        runningPids += pid
        return HostLauncher(
            hostDirectory = hostDirectory,
            bundledHostJar = if (bundled) BundledHostDirectory(bundledDirectory).readHostJar() else null,
            capabilities = capableLauncher,
            metadataReader = metadataReader,
            lockFiles = lockFiles,
            runningHostChannel = runningHostChannel,
            processTable = FakeProcessTable(currentPid = pid, runningPids = runningPids, startMillisOf = { it * 1000 }, onAwaitExit = onAwaitExit),
            log = { logLines += it },
            sleep = sleep,
        )
    }

    /** Launches as the process [pid]. */
    fun launch(pid: Long, afterPid: Long? = null, retryVersion: HostVersion? = null): LaunchOutcome = launcher(pid).launch(afterPid, retryVersion)

    /**
     * The host of the process [pid] takes `instance.lock` and publishes `instance.json`, as a host coming
     * up does, and its launcher lets `launch.lock` go on seeing it.
     */
    fun hostComesUp(pid: Long, starting: LaunchOutcome.Starting) {
        checkNotNull(lockFiles.of(pid).tryLock(hostDirectory.instanceLockFile)) { "the instance was taken" }
        runningHostChannel.publishInstanceJson(pid)
        starting.startOutcomeRecorder.releaseLaunchLock()
    }

    /** A process that is not a launch runs with the ID [pid]. */
    fun runOtherProcess(pid: Long) {
        runningPids += pid
    }

    /** The process [pid] ends without running anything more, as a crash or a kill ends it. */
    fun crash(pid: Long) {
        runningPids -= pid
        lockFiles.end(pid)
    }

    /** The process [pid] ends after its shutdown hook has run. */
    fun shutDown(pid: Long, starting: LaunchOutcome.Starting) {
        starting.startOutcomeRecorder.recordStartEndedWithoutFailure()
        crash(pid)
    }
}
