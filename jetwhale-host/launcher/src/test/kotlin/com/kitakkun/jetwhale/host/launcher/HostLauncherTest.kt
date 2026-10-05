package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HeldLock
import com.kitakkun.jetwhale.host.release.HostInstanceRecord
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataReader
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.HostVersionsDirectory
import com.kitakkun.jetwhale.host.release.LauncherState
import com.kitakkun.jetwhale.host.release.LockFiles
import com.kitakkun.jetwhale.host.release.ReleaseMetadataSignatureVerifier
import com.kitakkun.jetwhale.host.release.hostJarName
import org.junit.Assume
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class HostLauncherTest {
    private val appData: Path = Files.createTempDirectory("launcher-test")
    private val hostVersionsDirectory = HostVersionsDirectory(appData.resolve("host"))
    private val bundledDirectory = appData.resolve("package/host")
    private val lockFiles = FakeLockFiles()
    private val runningHost = FakeRunningHost(hostVersionsDirectory, answers = true)
    private val hostProcesses = FakeHostProcesses(lockFiles, hostVersionsDirectory, runningHost)
    private val logLines = CopyOnWriteArrayList<String>()
    private val waitedFor = CopyOnWriteArrayList<Long>()

    init {
        writeHostVersion(bundledDirectory, "1.0.0-alpha13") { it }
        Files.move(bundledDirectory.resolve(hostJarName(hostVersion("1.0.0-alpha13"), PLATFORM)), bundledDirectory.resolve(BundledHost.JAR_FILE_NAME))
    }

    @Test
    fun `starts the newest downloaded version that verifies`() {
        download("1.0.0-alpha14")
        download("1.0.0-alpha15")

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha15", assertIs<LaunchOutcome.Started>(outcome).version.name)
        assertEquals(listOf<Pair<String, String?>>("1.0.0-alpha15" to null), hostProcesses.starts)
        assertEquals(hostVersions("1.0.0-alpha15"), hostVersionsDirectory.readLauncherState().completedStartVersions)
    }

    @Test
    fun `starts the bundled version when no download is newer`() {
        download("1.0.0-alpha12")

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha13", assertIs<LaunchOutcome.Started>(outcome).version.name)
        assertFalse(versionDirectory("1.0.0-alpha12").exists(), "a version older than the bundled one goes")
    }

    @Test
    fun `deletes a version whose jar does not match its metadata and starts the next`() {
        download("1.0.0-alpha14")
        download("1.0.0-alpha15")
        Files.write(versionDirectory("1.0.0-alpha15").resolve(hostJarName(hostVersion("1.0.0-alpha15"), PLATFORM)), "tampered!!!!!!!!".toByteArray())

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha14", assertIs<LaunchOutcome.Started>(outcome).version.name)
        assertFalse(versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `deletes a version whose metadata does not read, or names another version`() {
        download("1.0.0-alpha15")
        Files.writeString(versionDirectory("1.0.0-alpha15").resolve("release.json"), "{ not json")
        writeHostVersion(versionDirectory("1.0.0-alpha16"), "1.0.0-alpha16") { it.copy(version = hostVersion("1.0.0-alpha17")) }

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha13", assertIs<LaunchOutcome.Started>(outcome).version.name)
        assertFalse(versionDirectory("1.0.0-alpha15").exists())
        assertFalse(versionDirectory("1.0.0-alpha16").exists())
    }

    @Test
    fun `deletes a version that its signature verifier does not trust`() {
        download("1.0.0-alpha15")

        val outcome = launcher(metadataReader = HostReleaseMetadataReader { _, _ -> false }).launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha13", assertIs<LaunchOutcome.Started>(outcome).version.name)
        assertFalse(versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `deletes a version directory that links outside the host directory, but not what it links to`() {
        val elsewhere = appData.resolve("elsewhere/1.0.0-alpha15")
        writeHostVersion(elsewhere, "1.0.0-alpha15") { it }
        Files.createDirectories(hostVersionsDirectory.root)
        Files.createSymbolicLink(versionDirectory("1.0.0-alpha15"), elsewhere)

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha13", assertIs<LaunchOutcome.Started>(outcome).version.name)
        assertFalse(Files.exists(versionDirectory("1.0.0-alpha15"), LinkOption.NOFOLLOW_LINKS))
        assertTrue(Files.exists(elsewhere.resolve("release.json")))
    }

    @Test
    fun `keeps but skips a version it refuses`() {
        download("1.0.0-alpha14")
        writeHostVersion(versionDirectory("1.0.0-alpha15"), "1.0.0-alpha15") { it.copy(launcherContract = 2) }

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha14", assertIs<LaunchOutcome.Started>(outcome).version.name)
        assertTrue(versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `skips a set-aside version`() {
        download("1.0.0-alpha14")
        download("1.0.0-alpha15")
        hostVersionsDirectory.writeLauncherState(LauncherState(completedStartVersions = emptySet(), setAsideVersions = hostVersions("1.0.0-alpha15")))

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha14", assertIs<LaunchOutcome.Started>(outcome).version.name)
        assertEquals(listOf<Pair<String, String?>>("1.0.0-alpha14" to null), hostProcesses.starts)
        assertTrue(versionDirectory("1.0.0-alpha15").exists(), "the user can still try it again")
        assertEquals(hostVersions("1.0.0-alpha15"), hostVersionsDirectory.readLauncherState().setAsideVersions)
    }

    @Test
    fun `sets aside a new version that fails twice and starts the next one with its name`() {
        download("1.0.0-alpha14")
        download("1.0.0-alpha15")
        hostProcesses.script("1.0.0-alpha15", FakeHostBehavior.ExitsBeforePublishing(1), FakeHostBehavior.ExitsBeforePublishing(134))

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha14", assertIs<LaunchOutcome.Started>(outcome).version.name)
        assertEquals(
            listOf<Pair<String, String?>>("1.0.0-alpha15" to null, "1.0.0-alpha15" to null, "1.0.0-alpha14" to "1.0.0-alpha15"),
            hostProcesses.starts,
        )
        assertEquals(LauncherState(completedStartVersions = hostVersions("1.0.0-alpha14"), setAsideVersions = hostVersions("1.0.0-alpha15")), hostVersionsDirectory.readLauncherState())
        assertTrue(versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `does not set aside a new version whose second start completes`() {
        download("1.0.0-alpha15")
        hostProcesses.script("1.0.0-alpha15", FakeHostBehavior.ExitsBeforePublishing(1))

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha15", assertIs<LaunchOutcome.Started>(outcome).version.name)
        assertEquals(2, hostProcesses.starts.size)
        assertEquals(emptySet(), hostVersionsDirectory.readLauncherState().setAsideVersions)
    }

    @Test
    fun `leaves a crash of a version that has completed a start to its crash recovery`() {
        download("1.0.0-alpha14")
        download("1.0.0-alpha15")
        hostVersionsDirectory.writeLauncherState(LauncherState(completedStartVersions = hostVersions("1.0.0-alpha15"), setAsideVersions = emptySet()))
        hostProcesses.script("1.0.0-alpha15", FakeHostBehavior.ExitsBeforePublishing(1))

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.Crashed(hostVersion("1.0.0-alpha15"), exitStatus = 1), outcome)
        assertEquals(listOf<Pair<String, String?>>("1.0.0-alpha15" to null), hostProcesses.starts)
        assertEquals(emptySet(), hostVersionsDirectory.readLauncherState().setAsideVersions)
    }

    @Test
    fun `counts a normal exit after the host has published as neither failed nor completed`() {
        download("1.0.0-alpha15")
        hostProcesses.script("1.0.0-alpha15", FakeHostBehavior.ExitsAfterPublishing(0))

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.Neither, outcome)
        assertEquals(1, hostProcesses.starts.size)
        assertEquals(LauncherState.EMPTY, hostVersionsDirectory.readLauncherState())
    }

    @Test
    fun `counts an exit before publishing as a hand-off when another host holds the instance`() {
        download("1.0.0-alpha15")
        hostProcesses.script("1.0.0-alpha15", FakeHostBehavior.LosesTheInstance)

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.Neither, outcome)
        assertEquals(1, hostProcesses.starts.size)
        assertEquals(LauncherState.EMPTY, hostVersionsDirectory.readLauncherState())
    }

    @Test
    fun `counts a normal exit before the host has published as neither failed nor completed`() {
        download("1.0.0-alpha15")
        hostProcesses.script("1.0.0-alpha15", FakeHostBehavior.ExitsBeforePublishing(0))

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.Neither, outcome)
        assertEquals(1, hostProcesses.starts.size)
        assertEquals(LauncherState.EMPTY, hostVersionsDirectory.readLauncherState())
    }

    @Test
    fun `counts a crash after publishing as a failed start`() {
        download("1.0.0-alpha15")
        hostProcesses.script("1.0.0-alpha15", FakeHostBehavior.ExitsAfterPublishing(1), FakeHostBehavior.ExitsAfterPublishing(137))

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha13", assertIs<LaunchOutcome.Started>(outcome).version.name)
        assertEquals(hostVersions("1.0.0-alpha15"), hostVersionsDirectory.readLauncherState().setAsideVersions)
    }

    @Test
    fun `holds launch lock across the retry and the fallback while no host has published`() {
        download("1.0.0-alpha15")
        hostProcesses.script("1.0.0-alpha15", FakeHostBehavior.ExitsBeforePublishing(1), FakeHostBehavior.ExitsBeforePublishing(1))
        val launchLockEvents = CopyOnWriteArrayList<String>()

        launcher(lockFiles = recordingLaunchLock(launchLockEvents)).launch(afterPid = null, retryVersion = null)

        assertEquals(listOf("lock", "release", "lock", "release"), launchLockEvents, "released only when the bundled host published")
    }

    @Test
    fun `has nothing left when the bundled version fails its first starts too`() {
        download("1.0.0-alpha15")
        hostProcesses.script("1.0.0-alpha15", FakeHostBehavior.ExitsBeforePublishing(1), FakeHostBehavior.ExitsBeforePublishing(1))
        hostProcesses.script("1.0.0-alpha13", FakeHostBehavior.ExitsBeforePublishing(1), FakeHostBehavior.ExitsBeforePublishing(1))

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.NothingLeft, outcome)
        assertEquals(hostVersions("1.0.0-alpha15"), hostVersionsDirectory.readLauncherState().setAsideVersions, "the bundled version is never set aside")
    }

    @Test
    fun `keeps the running version and the newest newer one after a start`() {
        download("1.0.0-alpha14")
        download("1.0.0-alpha15")
        download("1.0.0-alpha16")
        download("1.0.0-alpha17")
        hostVersionsDirectory.writeLauncherState(
            LauncherState(completedStartVersions = hostVersions("1.0.0-alpha14"), setAsideVersions = hostVersions("1.0.0-alpha15", "1.0.0-alpha16", "1.0.0-alpha17")),
        )

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha14", assertIs<LaunchOutcome.Started>(outcome).version.name)
        assertEquals(listOf("1.0.0-alpha17", "1.0.0-alpha14"), hostVersionsDirectory.installedVersions().map { it.version.name })
        assertEquals(
            LauncherState(completedStartVersions = hostVersions("1.0.0-alpha14"), setAsideVersions = hostVersions("1.0.0-alpha17")),
            hostVersionsDirectory.readLauncherState(),
        )
        assertTrue(Files.exists(bundledDirectory.resolve("release.json")), "the bundled version is never deleted")
    }

    @Test
    fun `asks a running host to come forward instead of starting another`() {
        download("1.0.0-alpha15")
        val runningInstance = lockFiles.lock(hostVersionsDirectory.instanceLockFile)

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.ActivatedRunningHost, outcome)
        assertEquals(1, runningHost.activationRequests)
        assertTrue(hostProcesses.starts.isEmpty())
        runningInstance.close()
    }

    @Test
    fun `hands the launch to a host another launcher started before the retry, instead of starting one`() {
        download("1.0.0-alpha15")
        hostProcesses.script("1.0.0-alpha15", FakeHostBehavior.ExitsAfterPublishing(1), FakeHostBehavior.ExitsBeforePublishing(0))
        hostProcesses.whenPublished("1.0.0-alpha15") {
            lockFiles.lock(hostVersionsDirectory.instanceLockFile)
            runningHost.publish(pid = 42)
            Files.writeString(hostVersionsDirectory.hostLogFile(hostVersion("1.0.0-alpha15")), "replacement output")
        }

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.ActivatedRunningHost, outcome)
        assertEquals(1, hostProcesses.starts.size)
        assertEquals(1, runningHost.activationRequests)
        assertEquals("replacement output", Files.readString(hostVersionsDirectory.hostLogFile(hostVersion("1.0.0-alpha15"))))
    }

    @Test
    fun `reports a running host that does not answer`() {
        lockFiles.lock(hostVersionsDirectory.instanceLockFile)

        val outcome = launcher(runningHost = FakeRunningHost(hostVersionsDirectory, answers = false)).launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.RunningHostUnreachable, outcome)
        assertTrue(hostProcesses.starts.isEmpty())
    }

    @Test
    fun `restarts into the newest version once the restarting host has ended`() {
        download("1.0.0-alpha15")
        val restartingHost = lockFiles.lock(hostVersionsDirectory.instanceLockFile)
        val launcher = launcher(waitForProcessExit = { pid ->
            assertEquals(null, lockFiles.tryLock(hostVersionsDirectory.launchLockFile), "it waits under launch.lock")
            waitedFor += pid
            restartingHost.close()
        })

        val outcome = launcher.launch(afterPid = 42, retryVersion = null)

        assertEquals(listOf(42L), waitedFor)
        assertEquals("1.0.0-alpha15", assertIs<LaunchOutcome.Started>(outcome).version.name)
    }

    @Test
    fun `starts one host when two launchers start at once`() {
        download("1.0.0-alpha15")
        val firstLaunchDone = CountDownLatch(1)
        val outcomes = CopyOnWriteArrayList<LaunchOutcome>()

        val launchers = List(2) {
            val time = TestTimeSource()
            var polls = 0
            // The launcher that starts the host stays in its startup window until the other launch
            // is done, which it can be only once launch.lock is released for the published host.
            val startupWindow = StartupWindow(
                length = 30.seconds,
                pollInterval = 200.milliseconds,
                timeSource = time,
                sleep = {
                    if (polls++ == 0) firstLaunchDone.await(10, TimeUnit.SECONDS)
                    time += it
                },
            )
            thread {
                outcomes += launcher(startupWindow = startupWindow).launch(afterPid = null, retryVersion = null)
                firstLaunchDone.countDown()
            }
        }
        launchers.forEach(Thread::join)

        assertEquals(1, hostProcesses.starts.size)
        assertEquals(LaunchOutcome.ActivatedRunningHost, outcomes.first())
        assertIs<LaunchOutcome.Started>(outcomes.last())
    }

    @Test
    fun `tries a set-aside version again when asked to`() {
        download("1.0.0-alpha14")
        download("1.0.0-alpha15")
        hostVersionsDirectory.writeLauncherState(LauncherState(completedStartVersions = hostVersions("1.0.0-alpha14"), setAsideVersions = hostVersions("1.0.0-alpha15")))

        val outcome = launcher().launch(afterPid = null, retryVersion = hostVersion("1.0.0-alpha15"))

        assertEquals("1.0.0-alpha15", assertIs<LaunchOutcome.Started>(outcome).version.name)
        assertEquals(LauncherState(completedStartVersions = hostVersions("1.0.0-alpha15"), setAsideVersions = emptySet()), hostVersionsDirectory.readLauncherState())
    }

    @Test
    fun `takes launch lock again to record a start once the host has published its record`() {
        download("1.0.0-alpha15")
        val launchLockEvents = CopyOnWriteArrayList<String>()

        launcher(lockFiles = recordingLaunchLock(launchLockEvents)).launch(afterPid = null, retryVersion = null)

        assertEquals(listOf("lock", "release", "lock", "release"), launchLockEvents)
    }

    @Test
    fun `keeps a change another launcher made to the record while this one let go of the lock`() {
        download("1.0.0-alpha15")
        download("1.0.0-alpha16")
        hostVersionsDirectory.writeLauncherState(LauncherState(completedStartVersions = emptySet(), setAsideVersions = hostVersions("1.0.0-alpha16")))
        hostProcesses.whenPublished("1.0.0-alpha15") {
            hostVersionsDirectory.writeLauncherState(LauncherState(completedStartVersions = emptySet(), setAsideVersions = emptySet()))
        }

        launcher().launch(afterPid = null, retryVersion = null)

        assertEquals(LauncherState(completedStartVersions = hostVersions("1.0.0-alpha15"), setAsideVersions = emptySet()), hostVersionsDirectory.readLauncherState())
    }

    @Test
    fun `does not set aside a version another launcher recorded as completed while this one let go of the lock`() {
        download("1.0.0-alpha15")
        hostProcesses.script("1.0.0-alpha15", FakeHostBehavior.ExitsAfterPublishing(1))
        hostProcesses.whenPublished("1.0.0-alpha15") {
            hostVersionsDirectory.writeLauncherState(LauncherState(completedStartVersions = hostVersions("1.0.0-alpha15"), setAsideVersions = emptySet()))
        }

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.Crashed(hostVersion("1.0.0-alpha15"), exitStatus = 1), outcome)
        assertEquals(1, hostProcesses.starts.size)
        assertEquals(emptySet(), hostVersionsDirectory.readLauncherState().setAsideVersions)
    }

    @Test
    fun `records a start but prunes nothing once that host has ended`() {
        download("1.0.0-alpha14")
        download("1.0.0-alpha15")
        hostProcesses.script("1.0.0-alpha15", FakeHostBehavior.EndsRightAfterTheWindow(runningPolls = 151))

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha15", assertIs<LaunchOutcome.Started>(outcome).version.name)
        assertEquals(hostVersions("1.0.0-alpha15"), hostVersionsDirectory.readLauncherState().completedStartVersions)
        assertTrue(versionDirectory("1.0.0-alpha14").exists())
    }

    @Test
    fun `keeps a second launch waiting until the host it started has published its record`() {
        download("1.0.0-alpha15")
        hostProcesses.script("1.0.0-alpha15", FakeHostBehavior.ClaimsTheInstanceLate(runningPolls = 1))

        val (first, second) = launchAgainWhileTheHostComesUp()

        assertEquals(1, hostProcesses.starts.size)
        assertEquals("1.0.0-alpha15", assertIs<LaunchOutcome.Started>(first).version.name)
        assertEquals(LaunchOutcome.ActivatedRunningHost, second)
    }

    @Test
    fun `does not take a record an earlier host left behind for the new host's`() {
        download("1.0.0-alpha15")
        HostInstanceRecord.publish(hostVersionsDirectory, HostInstanceRecord(port = 0, pid = hostProcesses.nextPid, token = "stale"))
        hostProcesses.script("1.0.0-alpha15", FakeHostBehavior.ClaimsTheInstanceLate(runningPolls = 1))

        val (first, second) = launchAgainWhileTheHostComesUp()

        assertEquals(1, hostProcesses.starts.size)
        assertIs<LaunchOutcome.Started>(first)
        assertEquals(LaunchOutcome.ActivatedRunningHost, second)
    }

    @Test
    fun `clears a set-aside mark another launcher put on a version that then completed its start`() {
        download("1.0.0-alpha15")
        hostProcesses.whenPublished("1.0.0-alpha15") {
            hostVersionsDirectory.writeLauncherState(LauncherState(completedStartVersions = emptySet(), setAsideVersions = hostVersions("1.0.0-alpha15")))
        }

        launcher().launch(afterPid = null, retryVersion = null)

        assertEquals(LauncherState(completedStartVersions = hostVersions("1.0.0-alpha15"), setAsideVersions = emptySet()), hostVersionsDirectory.readLauncherState())
    }

    @Test
    fun `deletes a version whose jar cannot be read and starts the next`() {
        download("1.0.0-alpha15")
        val jar = versionDirectory("1.0.0-alpha15").resolve(hostJarName(hostVersion("1.0.0-alpha15"), PLATFORM)).toFile()
        Assume.assumeTrue("the file system cannot take read permission away", jar.setReadable(false) && !jar.canRead())

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha13", assertIs<LaunchOutcome.Started>(outcome).version.name)
        assertFalse(versionDirectory("1.0.0-alpha15").exists())
    }

    /**
     * Launches, and launches again from another thread during the first start's first poll, while its
     * host has not taken the instance yet. The first launch goes on once the second waits for a lock
     * or has ended. Returns both outcomes.
     */
    private fun launchAgainWhileTheHostComesUp(): Pair<LaunchOutcome, LaunchOutcome> {
        val secondWaitsOrEnded = CountDownLatch(1)
        val waitReportingLocks = object : LockFiles by lockFiles {
            override fun lock(path: Path): HeldLock = lockFiles.tryLock(path) ?: run {
                secondWaitsOrEnded.countDown()
                lockFiles.lock(path)
            }
        }
        var second: Thread? = null
        var secondOutcome: LaunchOutcome? = null
        val time = TestTimeSource()
        val startupWindow = StartupWindow(
            length = 30.seconds,
            pollInterval = 200.milliseconds,
            timeSource = time,
            sleep = {
                if (second == null) {
                    second = thread {
                        secondOutcome = launcher(lockFiles = waitReportingLocks).launch(afterPid = null, retryVersion = null)
                        secondWaitsOrEnded.countDown()
                    }
                    secondWaitsOrEnded.await(10, TimeUnit.SECONDS)
                }
                time += it
            },
        )

        val first = launcher(startupWindow = startupWindow).launch(afterPid = null, retryVersion = null)
        second?.join()
        return first to assertNotNull(secondOutcome)
    }

    private fun recordingLaunchLock(events: MutableList<String>) = object : LockFiles by lockFiles {
        override fun lock(path: Path): HeldLock {
            val held = lockFiles.lock(path)
            if (path == hostVersionsDirectory.launchLockFile) events += "lock"
            return HeldLock {
                if (path == hostVersionsDirectory.launchLockFile) events += "release"
                held.close()
            }
        }
    }

    private fun download(versionName: String) {
        writeHostVersion(versionDirectory(versionName), versionName) { it }
    }

    private fun versionDirectory(versionName: String): Path = hostVersionsDirectory.root.resolve(versionName)

    private fun hostVersions(vararg names: String): Set<HostVersion> = names.mapTo(LinkedHashSet(), ::hostVersion)

    private fun launcher(
        lockFiles: LockFiles = this.lockFiles,
        metadataReader: HostReleaseMetadataReader = HostReleaseMetadataReader(ReleaseMetadataSignatureVerifier.JetWhaleReleases),
        runningHost: RunningHostChannel = this.runningHost,
        startupWindow: StartupWindow = testStartupWindow(),
        waitForProcessExit: (Long) -> Unit = { waitedFor += it },
    ) = HostLauncher(
        hostVersionsDirectory = hostVersionsDirectory,
        bundledHost = BundledHost.read(bundledDirectory),
        capabilities = capableLauncher,
        metadataReader = metadataReader,
        lockFiles = lockFiles,
        runningHost = runningHost,
        hostProcesses = hostProcesses,
        startupWindow = startupWindow,
        log = { logLines += it },
        waitForProcessExit = waitForProcessExit,
    )

    private fun testStartupWindow(): StartupWindow {
        val time = TestTimeSource()
        return StartupWindow(length = 30.seconds, pollInterval = 200.milliseconds, timeSource = time, sleep = time::plusAssign)
    }
}
