package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HeldLock
import com.kitakkun.jetwhale.host.release.HostInstanceRecord
import com.kitakkun.jetwhale.host.release.HostPlatformRelease
import com.kitakkun.jetwhale.host.release.HostReleaseMetadata
import com.kitakkun.jetwhale.host.release.HostRuntimeRequirements
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.HostVersionsDirectory
import com.kitakkun.jetwhale.host.release.LauncherCapabilities
import com.kitakkun.jetwhale.host.release.LockFiles
import com.kitakkun.jetwhale.host.release.hostJarName
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicLong

const val PLATFORM = "macos-arm64"

val capableLauncher = LauncherCapabilities(
    contract = 1,
    javaFeatureVersion = 21,
    modules = setOf("java.base", "java.desktop"),
    platformKey = PLATFORM,
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
            jvmArgs = listOf("-Xdock:name=JetWhale Debugger"),
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

/** OS file locks as separate processes see them, for launchers and hosts that run as threads. */
class FakeLockFiles : LockFiles {
    private val semaphores = ConcurrentHashMap<Path, Semaphore>()

    override fun lock(path: Path): HeldLock {
        val semaphore = semaphoreOf(path)
        semaphore.acquire()
        return HeldLock(semaphore::release)
    }

    override fun tryLock(path: Path): HeldLock? {
        val semaphore = semaphoreOf(path)
        return if (semaphore.tryAcquire()) HeldLock(semaphore::release) else null
    }

    private fun semaphoreOf(path: Path): Semaphore = semaphores.computeIfAbsent(path) { Semaphore(1) }
}

/** What a fake host does once started. */
sealed interface FakeHostBehavior {
    /** Takes the instance, publishes its record and keeps running. */
    data object Runs : FakeHostBehavior

    /** Exits with [status] before it has published its record. */
    data class ExitsBeforePublishing(val status: Int) : FakeHostBehavior

    /** Publishes its record, then exits with [status] within the startup window. */
    data class ExitsAfterPublishing(val status: Int) : FakeHostBehavior

    /** Finds the instance taken by another host as it starts, and exits normally without publishing. */
    data object LosesTheInstance : FakeHostBehavior

    /** Publishes its record and reports running for [runningPolls] polls, then reports an exit. */
    data class EndsRightAfterTheWindow(val runningPolls: Int) : FakeHostBehavior

    /**
     * Reports running for [runningPolls] polls before it takes the instance and publishes its record,
     * as a host does while its JVM and window come up. When the instance is taken by then, it exits
     * normally, as a host that hands the launch to the running one.
     */
    data class ClaimsTheInstanceLate(val runningPolls: Int) : FakeHostBehavior
}

class FakeHostProcesses(
    private val lockFiles: LockFiles,
    private val instanceLockFile: Path,
    private val runningHost: FakeRunningHost,
) : HostProcesses {
    private val pids = AtomicLong(1000)
    private val scripts = ConcurrentHashMap<String, MutableList<FakeHostBehavior>>()
    private val afterPublishing = ConcurrentHashMap<String, () -> Unit>()

    /** The process ID the next start gets. */
    val nextPid: Long get() = pids.get() + 1

    /**
     * Runs [action] once the host of the version named [versionName] has published its record, while
     * the launcher has let go of `launch.lock`: what another launcher could do in between.
     */
    fun whenPublished(versionName: String, action: () -> Unit) {
        afterPublishing[versionName] = action
    }

    /** Every start, as the version's name and the name of the set-aside version it was told about. */
    val starts: List<Pair<String, String?>> get() = recordedStarts

    private val recordedStarts = CopyOnWriteArrayList<Pair<String, String?>>()

    /** What the next starts of the version named [versionName] do, in order; once they are used up, it runs. */
    fun script(versionName: String, vararg behaviors: FakeHostBehavior) {
        scripts[versionName] = behaviors.toMutableList()
    }

    override fun start(start: HostStart, setAsideVersion: HostVersion?): HostProcess {
        recordedStarts += start.version.name to setAsideVersion?.name
        val behavior = scripts[start.version.name]?.removeFirstOrNull() ?: FakeHostBehavior.Runs
        val pid = pids.incrementAndGet()
        return when (behavior) {
            is FakeHostBehavior.Runs -> {
                checkNotNull(lockFiles.tryLock(instanceLockFile)) { "a started host found the instance taken" }
                runningHost.publish(pid)
                afterPublishing[start.version.name]?.invoke()
                FakeHostProcess(pid, null)
            }

            is FakeHostBehavior.ExitsBeforePublishing -> FakeHostProcess(pid, behavior.status)

            is FakeHostBehavior.ExitsAfterPublishing -> {
                runningHost.publish(pid)
                afterPublishing[start.version.name]?.invoke()
                FakeHostProcess(pid, behavior.status)
            }

            is FakeHostBehavior.EndsRightAfterTheWindow -> {
                checkNotNull(lockFiles.tryLock(instanceLockFile)) { "a started host found the instance taken" }
                runningHost.publish(pid)
                EndingHostProcess(pid, runningPolls = behavior.runningPolls)
            }

            is FakeHostBehavior.LosesTheInstance -> {
                checkNotNull(lockFiles.tryLock(instanceLockFile)) { "the other host could not take the instance" }
                FakeHostProcess(pid, 0)
            }

            is FakeHostBehavior.ClaimsTheInstanceLate -> LateClaimingHostProcess(pid, behavior.runningPolls) {
                if (lockFiles.tryLock(instanceLockFile) == null) {
                    0
                } else {
                    runningHost.publish(pid)
                    afterPublishing[start.version.name]?.invoke()
                    null
                }
            }
        }
    }
}

/** Reports running for [runningPolls] polls, then runs [claim] once and reports the exit status it returns from then on. */
class LateClaimingHostProcess(
    override val pid: Long,
    private val runningPolls: Int,
    private val claim: () -> Int?,
) : HostProcess {
    private var polls = 0
    private var exitStatus: Int? = null

    override fun exitStatus(): Int? {
        if (polls++ == runningPolls) exitStatus = claim()
        return exitStatus
    }

    override fun waitForExit(): Int = exitStatus ?: 0
}

/** Reports running for [runningPolls] polls, then status 0. */
class EndingHostProcess(override val pid: Long, private val runningPolls: Int) : HostProcess {
    private var polls = 0

    override fun exitStatus(): Int? = if (polls++ < runningPolls) null else 0

    override fun waitForExit(): Int = 0
}

class FakeHostProcess(override val pid: Long, private val exitStatus: Int?) : HostProcess {
    override fun exitStatus(): Int? = exitStatus

    override fun waitForExit(): Int = exitStatus ?: 0
}

/**
 * Publication goes through the real `instance.json`, as a host's would; a request to come forward
 * only counts and answers.
 */
class FakeRunningHost(
    private val hostVersionsDirectory: HostVersionsDirectory,
    private val answers: Boolean,
) : RunningHostChannel {
    var activationRequests = 0
        private set

    fun publish(pid: Long) {
        HostInstanceRecord.publish(hostVersionsDirectory, HostInstanceRecord(port = 0, pid = pid, token = "token"))
    }

    override fun isPublishedBy(pid: Long): Boolean = HostInstanceRecord.read(hostVersionsDirectory)?.pid == pid

    override fun requestActivation(): Boolean {
        activationRequests++
        return answers
    }
}
