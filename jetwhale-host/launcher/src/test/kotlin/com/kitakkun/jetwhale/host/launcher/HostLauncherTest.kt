package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HeldLock
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataReader
import com.kitakkun.jetwhale.host.release.HostVersionsDirectory
import com.kitakkun.jetwhale.host.release.InstalledHostVersion
import com.kitakkun.jetwhale.host.release.LauncherState
import com.kitakkun.jetwhale.host.release.LockFiles
import com.kitakkun.jetwhale.host.release.ReleaseMetadataSignatureVerifier
import com.kitakkun.jetwhale.host.release.hostJarName
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class HostLauncherTest {
    private val appData: Path = Files.createTempDirectory("launcher-test")
    private val versions = HostVersionsDirectory(appData.resolve("host"))
    private val bundledDirectory = appData.resolve("package/host")
    private val locks = FakeLocks()
    private val runningHost = FakeRunningHost(answers = true)
    private val processes = FakeHostProcesses(locks, versions.instanceLock, runningHost)
    private val logLines = CopyOnWriteArrayList<String>()
    private val waitedFor = CopyOnWriteArrayList<Long>()

    init {
        writeHostVersion(bundledDirectory, "1.0.0-alpha13") { it }
        Files.move(bundledDirectory.resolve(hostJarName("1.0.0-alpha13", PLATFORM)), bundledDirectory.resolve(BundledHost.JAR_FILE_NAME))
    }

    @Test
    fun `starts the newest downloaded version that verifies`() {
        download("1.0.0-alpha14")
        download("1.0.0-alpha15")

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha15", assertIs<LaunchOutcome.Started>(outcome).version)
        assertEquals(listOf<Pair<String, String?>>("1.0.0-alpha15" to null), processes.starts)
        assertEquals(setOf("1.0.0-alpha15"), versions.readLauncherState().completedStarts)
    }

    @Test
    fun `starts the bundled version when no download is newer`() {
        download("1.0.0-alpha12")

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha13", assertIs<LaunchOutcome.Started>(outcome).version)
        assertFalse(versionDirectory("1.0.0-alpha12").exists(), "a version older than the bundled one goes")
    }

    @Test
    fun `deletes a version whose jar does not match its metadata and starts the next`() {
        download("1.0.0-alpha14")
        download("1.0.0-alpha15")
        Files.write(versionDirectory("1.0.0-alpha15").resolve(hostJarName("1.0.0-alpha15", PLATFORM)), "tampered!!!!!!!!".toByteArray())

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha14", assertIs<LaunchOutcome.Started>(outcome).version)
        assertFalse(versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `deletes a version whose metadata does not read, or names another version`() {
        download("1.0.0-alpha15")
        Files.writeString(versionDirectory("1.0.0-alpha15").resolve("release.json"), "{ not json")
        writeHostVersion(versionDirectory("1.0.0-alpha16"), "1.0.0-alpha16") { it.copy(version = "1.0.0-alpha17") }

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha13", assertIs<LaunchOutcome.Started>(outcome).version)
        assertFalse(versionDirectory("1.0.0-alpha15").exists())
        assertFalse(versionDirectory("1.0.0-alpha16").exists())
    }

    @Test
    fun `deletes a version that its signature verifier does not trust`() {
        download("1.0.0-alpha15")

        val outcome = launcher(metadataReader = HostReleaseMetadataReader { _, _ -> false }).launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha13", assertIs<LaunchOutcome.Started>(outcome).version)
        assertFalse(versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `deletes a version directory that links outside the host directory, but not what it links to`() {
        val elsewhere = appData.resolve("elsewhere/1.0.0-alpha15")
        writeHostVersion(elsewhere, "1.0.0-alpha15") { it }
        Files.createDirectories(versions.root)
        Files.createSymbolicLink(versionDirectory("1.0.0-alpha15"), elsewhere)

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha13", assertIs<LaunchOutcome.Started>(outcome).version)
        assertFalse(Files.exists(versionDirectory("1.0.0-alpha15"), LinkOption.NOFOLLOW_LINKS))
        assertTrue(Files.exists(elsewhere.resolve("release.json")))
    }

    @Test
    fun `keeps but skips a version it refuses`() {
        download("1.0.0-alpha14")
        writeHostVersion(versionDirectory("1.0.0-alpha15"), "1.0.0-alpha15") { it.copy(launcherContract = 2) }

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha14", assertIs<LaunchOutcome.Started>(outcome).version)
        assertTrue(versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `skips a set-aside version`() {
        download("1.0.0-alpha14")
        download("1.0.0-alpha15")
        versions.writeLauncherState(LauncherState(completedStarts = emptySet(), setAside = setOf("1.0.0-alpha15")))

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha14", assertIs<LaunchOutcome.Started>(outcome).version)
        assertEquals(listOf<Pair<String, String?>>("1.0.0-alpha14" to null), processes.starts)
        assertTrue(versionDirectory("1.0.0-alpha15").exists(), "the user can still try it again")
        assertEquals(setOf("1.0.0-alpha15"), versions.readLauncherState().setAside)
    }

    @Test
    fun `sets aside a new version that fails twice and starts the next one with its name`() {
        download("1.0.0-alpha14")
        download("1.0.0-alpha15")
        processes.script("1.0.0-alpha15", FakeHostBehavior.ExitsBeforePublishing(1), FakeHostBehavior.ExitsBeforePublishing(134))

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha14", assertIs<LaunchOutcome.Started>(outcome).version)
        assertEquals(
            listOf<Pair<String, String?>>("1.0.0-alpha15" to null, "1.0.0-alpha15" to null, "1.0.0-alpha14" to "1.0.0-alpha15"),
            processes.starts,
        )
        assertEquals(LauncherState(completedStarts = setOf("1.0.0-alpha14"), setAside = setOf("1.0.0-alpha15")), versions.readLauncherState())
        assertTrue(versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `does not set aside a new version whose second start completes`() {
        download("1.0.0-alpha15")
        processes.script("1.0.0-alpha15", FakeHostBehavior.ExitsBeforePublishing(1))

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha15", assertIs<LaunchOutcome.Started>(outcome).version)
        assertEquals(2, processes.starts.size)
        assertEquals(emptySet(), versions.readLauncherState().setAside)
    }

    @Test
    fun `leaves a crash of a version that has completed a start to its crash recovery`() {
        download("1.0.0-alpha14")
        download("1.0.0-alpha15")
        versions.writeLauncherState(LauncherState(completedStarts = setOf("1.0.0-alpha15"), setAside = emptySet()))
        processes.script("1.0.0-alpha15", FakeHostBehavior.ExitsBeforePublishing(1))

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.Crashed("1.0.0-alpha15", exitStatus = 1), outcome)
        assertEquals(listOf<Pair<String, String?>>("1.0.0-alpha15" to null), processes.starts)
        assertEquals(emptySet(), versions.readLauncherState().setAside)
    }

    @Test
    fun `counts a normal exit after the host has published as neither failed nor completed`() {
        download("1.0.0-alpha15")
        processes.script("1.0.0-alpha15", FakeHostBehavior.ExitsAfterPublishing(0))

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.Neither, outcome)
        assertEquals(1, processes.starts.size)
        assertEquals(LauncherState.EMPTY, versions.readLauncherState())
    }

    @Test
    fun `counts an exit before publishing as a hand-off when another host holds the instance`() {
        download("1.0.0-alpha15")
        processes.script("1.0.0-alpha15", FakeHostBehavior.LosesTheInstance)

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.Neither, outcome)
        assertEquals(1, processes.starts.size)
        assertEquals(LauncherState.EMPTY, versions.readLauncherState())
    }

    @Test
    fun `counts a normal exit before publishing as a failed start`() {
        download("1.0.0-alpha15")
        processes.script("1.0.0-alpha15", FakeHostBehavior.ExitsBeforePublishing(0), FakeHostBehavior.ExitsBeforePublishing(0))

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha13", assertIs<LaunchOutcome.Started>(outcome).version)
        assertEquals(setOf("1.0.0-alpha15"), versions.readLauncherState().setAside)
    }

    @Test
    fun `counts a crash after publishing as a failed start`() {
        download("1.0.0-alpha15")
        processes.script("1.0.0-alpha15", FakeHostBehavior.ExitsAfterPublishing(1), FakeHostBehavior.ExitsAfterPublishing(137))

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha13", assertIs<LaunchOutcome.Started>(outcome).version)
        assertEquals(setOf("1.0.0-alpha15"), versions.readLauncherState().setAside)
    }

    @Test
    fun `holds launch lock across the retry and the fallback while no host has published`() {
        download("1.0.0-alpha15")
        processes.script("1.0.0-alpha15", FakeHostBehavior.ExitsBeforePublishing(1), FakeHostBehavior.ExitsBeforePublishing(1))
        val launchLockEvents = CopyOnWriteArrayList<String>()

        launcher(locks = recordingLaunchLock(launchLockEvents)).launch(afterPid = null, retryVersion = null)

        assertEquals(listOf("lock", "release", "lock", "release"), launchLockEvents, "released only when the bundled host published")
    }

    @Test
    fun `has nothing left when the bundled version fails its first starts too`() {
        download("1.0.0-alpha15")
        processes.script("1.0.0-alpha15", FakeHostBehavior.ExitsBeforePublishing(1), FakeHostBehavior.ExitsBeforePublishing(1))
        processes.script("1.0.0-alpha13", FakeHostBehavior.ExitsBeforePublishing(1), FakeHostBehavior.ExitsBeforePublishing(1))

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.NothingLeft, outcome)
        assertEquals(setOf("1.0.0-alpha15"), versions.readLauncherState().setAside, "the bundled version is never set aside")
    }

    @Test
    fun `keeps the running version and the newest newer one after a start`() {
        download("1.0.0-alpha14")
        download("1.0.0-alpha15")
        download("1.0.0-alpha16")
        download("1.0.0-alpha17")
        versions.writeLauncherState(
            LauncherState(completedStarts = setOf("1.0.0-alpha14"), setAside = setOf("1.0.0-alpha15", "1.0.0-alpha16", "1.0.0-alpha17")),
        )

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha14", assertIs<LaunchOutcome.Started>(outcome).version)
        assertEquals(listOf("1.0.0-alpha17", "1.0.0-alpha14"), versions.installedVersions().map(InstalledHostVersion::name))
        assertEquals(
            LauncherState(completedStarts = setOf("1.0.0-alpha14"), setAside = setOf("1.0.0-alpha17")),
            versions.readLauncherState(),
        )
        assertTrue(Files.exists(bundledDirectory.resolve("release.json")), "the bundled version is never deleted")
    }

    @Test
    fun `asks a running host to come forward instead of starting another`() {
        download("1.0.0-alpha15")
        val runningInstance = locks.lock(versions.instanceLock)

        val outcome = launcher().launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.ActivatedRunningHost, outcome)
        assertEquals(1, runningHost.activationRequests)
        assertTrue(processes.starts.isEmpty())
        runningInstance.close()
    }

    @Test
    fun `reports a running host that does not answer`() {
        locks.lock(versions.instanceLock)

        val outcome = launcher(runningHost = FakeRunningHost(answers = false)).launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.RunningHostUnreachable, outcome)
        assertTrue(processes.starts.isEmpty())
    }

    @Test
    fun `restarts into the newest version once the restarting host has ended`() {
        download("1.0.0-alpha15")
        val restartingHost = locks.lock(versions.instanceLock)
        val launcher = launcher(waitForProcessExit = { pid ->
            assertEquals(null, locks.tryLock(versions.launchLock), "it waits under launch.lock")
            waitedFor += pid
            restartingHost.close()
        })

        val outcome = launcher.launch(afterPid = 42, retryVersion = null)

        assertEquals(listOf(42L), waitedFor)
        assertEquals("1.0.0-alpha15", assertIs<LaunchOutcome.Started>(outcome).version)
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

        assertEquals(1, processes.starts.size)
        assertEquals(LaunchOutcome.ActivatedRunningHost, outcomes.first())
        assertIs<LaunchOutcome.Started>(outcomes.last())
    }

    @Test
    fun `tries a set-aside version again when asked to`() {
        download("1.0.0-alpha14")
        download("1.0.0-alpha15")
        versions.writeLauncherState(LauncherState(completedStarts = setOf("1.0.0-alpha14"), setAside = setOf("1.0.0-alpha15")))

        val outcome = launcher().launch(afterPid = null, retryVersion = "1.0.0-alpha15")

        assertEquals("1.0.0-alpha15", assertIs<LaunchOutcome.Started>(outcome).version)
        assertEquals(LauncherState(completedStarts = setOf("1.0.0-alpha15"), setAside = emptySet()), versions.readLauncherState())
    }

    @Test
    fun `takes launch lock again to record a start once the host has published its record`() {
        download("1.0.0-alpha15")
        val launchLockEvents = CopyOnWriteArrayList<String>()

        launcher(locks = recordingLaunchLock(launchLockEvents)).launch(afterPid = null, retryVersion = null)

        assertEquals(listOf("lock", "release", "lock", "release"), launchLockEvents)
    }

    private fun recordingLaunchLock(events: MutableList<String>) = object : LockFiles by locks {
        override fun lock(path: Path): HeldLock {
            val held = locks.lock(path)
            if (path == versions.launchLock) events += "lock"
            return HeldLock {
                if (path == versions.launchLock) events += "release"
                held.close()
            }
        }
    }

    private fun download(version: String) {
        writeHostVersion(versionDirectory(version), version) { it }
    }

    private fun versionDirectory(version: String): Path = versions.root.resolve(version)

    private fun launcher(
        locks: LockFiles = this.locks,
        metadataReader: HostReleaseMetadataReader = HostReleaseMetadataReader(ReleaseMetadataSignatureVerifier.JetWhaleReleases),
        runningHost: RunningHostChannel = this.runningHost,
        startupWindow: StartupWindow = testStartupWindow(),
        waitForProcessExit: (Long) -> Unit = { waitedFor += it },
    ) = HostLauncher(
        versions = versions,
        bundled = BundledHost.read(bundledDirectory),
        capabilities = capableLauncher,
        metadataReader = metadataReader,
        locks = locks,
        runningHost = runningHost,
        hostProcesses = processes,
        startupWindow = startupWindow,
        log = { logLines += it },
        waitForProcessExit = waitForProcessExit,
    )

    private fun testStartupWindow(): StartupWindow {
        val time = TestTimeSource()
        return StartupWindow(length = 30.seconds, pollInterval = 200.milliseconds, timeSource = time, sleep = time::plusAssign)
    }
}
