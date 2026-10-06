package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HeldLock
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataReader
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.InstanceJson
import com.kitakkun.jetwhale.host.release.LauncherState
import com.kitakkun.jetwhale.host.release.LockFiles
import com.kitakkun.jetwhale.host.release.StartingHost
import com.kitakkun.jetwhale.host.release.hostJarName
import org.junit.Assume
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostLauncherTest {
    private val bed = LaunchTestBed()
    private val hostVersionsDirectory = bed.hostVersionsDirectory

    @Test
    fun `starts the newest downloaded version that verifies and writes its start down`() {
        bed.download("1.0.0-alpha14")
        bed.download("1.0.0-alpha15")

        val starting = starting(bed.launch(pid = 101))

        assertEquals("1.0.0-alpha15", starting.start.version.name)
        assertNull(starting.setAsideVersion)
        assertEquals(StartingHost(hostVersion("1.0.0-alpha15"), pid = 101, processStartMillis = 101_000), state().startingHost)
    }

    @Test
    fun `starts the bundled version when no download is newer`() {
        bed.download("1.0.0-alpha12")

        val starting = starting(bed.launch(pid = 101))
        completeStart(101, starting)

        assertEquals("1.0.0-alpha13", starting.start.version.name)
        assertTrue(starting.start.isBundled)
        assertFalse(bed.versionDirectory("1.0.0-alpha12").exists(), "a version older than the bundled one goes")
    }

    @Test
    fun `deletes a version whose jar does not match its metadata and starts the next`() {
        bed.download("1.0.0-alpha14")
        bed.download("1.0.0-alpha15")
        Files.write(bed.versionDirectory("1.0.0-alpha15").resolve(hostJarName(hostVersion("1.0.0-alpha15"), PLATFORM)), "tampered!!!!!!!!".toByteArray())

        assertEquals("1.0.0-alpha14", starting(bed.launch(pid = 101)).start.version.name)
        assertFalse(bed.versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `deletes a version whose metadata does not read, or names another version`() {
        bed.download("1.0.0-alpha15")
        Files.writeString(bed.versionDirectory("1.0.0-alpha15").resolve("release.json"), "{ not json")
        writeHostVersion(bed.versionDirectory("1.0.0-alpha16"), "1.0.0-alpha16") { it.copy(version = hostVersion("1.0.0-alpha17")) }

        assertEquals("1.0.0-alpha13", starting(bed.launch(pid = 101)).start.version.name)
        assertFalse(bed.versionDirectory("1.0.0-alpha15").exists())
        assertFalse(bed.versionDirectory("1.0.0-alpha16").exists())
    }

    @Test
    fun `deletes a version that its signature verifier does not trust`() {
        bed.download("1.0.0-alpha15")

        val outcome = bed.launcher(pid = 101, metadataReader = HostReleaseMetadataReader { _, _ -> false }).launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha13", starting(outcome).start.version.name)
        assertFalse(bed.versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `deletes a version directory that links outside the host directory, but not what it links to`() {
        val elsewhere = bed.appData.resolve("elsewhere/1.0.0-alpha15")
        writeHostVersion(elsewhere, "1.0.0-alpha15") { it }
        Files.createDirectories(hostVersionsDirectory.root)
        Files.createSymbolicLink(bed.versionDirectory("1.0.0-alpha15"), elsewhere)

        assertEquals("1.0.0-alpha13", starting(bed.launch(pid = 101)).start.version.name)
        assertFalse(Files.exists(bed.versionDirectory("1.0.0-alpha15"), LinkOption.NOFOLLOW_LINKS))
        assertTrue(Files.exists(elsewhere.resolve("release.json")))
    }

    @Test
    fun `deletes a version whose jar cannot be read and starts the next`() {
        bed.download("1.0.0-alpha15")
        val jar = bed.versionDirectory("1.0.0-alpha15").resolve(hostJarName(hostVersion("1.0.0-alpha15"), PLATFORM)).toFile()
        Assume.assumeTrue("the file system cannot take read permission away", jar.setReadable(false) && !jar.canRead())

        assertEquals("1.0.0-alpha13", starting(bed.launch(pid = 101)).start.version.name)
        assertFalse(bed.versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `keeps but skips a version it refuses`() {
        bed.download("1.0.0-alpha14")
        writeHostVersion(bed.versionDirectory("1.0.0-alpha15"), "1.0.0-alpha15") { it.copy(launcherContract = 2) }

        assertEquals("1.0.0-alpha14", starting(bed.launch(pid = 101)).start.version.name)
        assertTrue(bed.versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `keeps but skips a version that needs a JVM argument this JVM did not start with`() {
        bed.download("1.0.0-alpha14")
        writeHostVersion(bed.versionDirectory("1.0.0-alpha15"), "1.0.0-alpha15") { it.copy(jvmArgs = it.jvmArgs + "--enable-native-access=ALL-UNNAMED") }

        assertEquals("1.0.0-alpha14", starting(bed.launch(pid = 101)).start.version.name)
        assertTrue(bed.versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `skips a set-aside version`() {
        bed.download("1.0.0-alpha14")
        bed.download("1.0.0-alpha15")
        writeState(setAsideVersions = versions("1.0.0-alpha15"))

        assertEquals("1.0.0-alpha14", starting(bed.launch(pid = 101)).start.version.name)
        assertTrue(bed.versionDirectory("1.0.0-alpha15").exists(), "the user can still try it again")
        assertEquals(versions("1.0.0-alpha15"), state().setAsideVersions)
    }

    @Test
    fun `holds launch lock until the host publishes its instance JSON, and takes it again to record the completed start`() {
        bed.download("1.0.0-alpha15")
        val events = CopyOnWriteArrayList<String>()

        val launcher = bed.launcher(pid = 101, lockFiles = recordingLaunchLock(bed.lockFiles.of(101), events))
        val starting = starting(launcher.launch(afterPid = null, retryVersion = null))
        events += "launched"
        bed.hostComesUp(101, starting)
        events += "published"
        bed.watchWindow(starting, publishedBy = 101)

        assertEquals(listOf("lock", "launched", "release", "published", "lock", "release"), events)
        assertEquals(versions("1.0.0-alpha15"), state().completedStartVersions)
        assertNull(state().startingHost)
    }

    @Test
    fun `counts a start whose process ended within its window without a record as failed, and starts the version again`() {
        bed.download("1.0.0-alpha14")
        bed.download("1.0.0-alpha15")
        bed.hostComesUp(101, starting(bed.launch(pid = 101)))
        bed.crash(101)

        val again = starting(bed.launch(pid = 102))

        assertEquals("1.0.0-alpha15", again.start.version.name)
        assertNull(again.setAsideVersion)
        assertEquals(mapOf(hostVersion("1.0.0-alpha15") to 1), state().failedStartCounts)
        assertEquals(102L, state().startingHost?.pid)
    }

    @Test
    fun `sets aside a version that failed two starts in a row and starts the next one with its name`() {
        bed.download("1.0.0-alpha14")
        bed.download("1.0.0-alpha15")
        bed.launch(pid = 101)
        bed.crash(101)
        bed.launch(pid = 102)
        bed.crash(102)

        val fallback = starting(bed.launch(pid = 103))

        assertEquals("1.0.0-alpha14", fallback.start.version.name)
        assertEquals("1.0.0-alpha15", fallback.setAsideVersion?.name)
        assertEquals(versions("1.0.0-alpha15"), state().setAsideVersions)
        assertTrue(bed.versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `counts a throw from the host's main as a failed start, and lets go of launch lock`() {
        bed.download("1.0.0-alpha15")

        starting(bed.launch(pid = 101)).startup.hostFailed()

        assertEquals(mapOf(hostVersion("1.0.0-alpha15") to 1), state().failedStartCounts)
        assertNull(state().startingHost)
        assertFalse(bed.lockFiles.isHeld(hostVersionsDirectory.launchLockFile))
    }

    @Test
    fun `sets aside a version after a crash and a throw from its main`() {
        bed.download("1.0.0-alpha15")
        bed.launch(pid = 101)
        bed.crash(101)
        starting(bed.launch(pid = 102)).startup.hostFailed()
        bed.crash(102)

        val fallback = starting(bed.launch(pid = 103))

        assertEquals("1.0.0-alpha13", fallback.start.version.name)
        assertEquals("1.0.0-alpha15", fallback.setAsideVersion?.name)
    }

    @Test
    fun `counts a shutdown within the window as neither failed nor completed, before the host has published or after`() {
        bed.download("1.0.0-alpha15")
        bed.shutDown(101, starting(bed.launch(pid = 101)))
        val published = starting(bed.launch(pid = 102))
        bed.hostComesUp(102, published)
        bed.shutDown(102, published)

        val again = starting(bed.launch(pid = 103))

        assertEquals("1.0.0-alpha15", again.start.version.name)
        assertEquals(emptyMap(), state().failedStartCounts)
        assertEquals(emptySet(), state().setAsideVersions)
        assertEquals(emptySet(), state().completedStartVersions)
    }

    @Test
    fun `records nothing more when the host fails or the JVM shuts down after a completed start`() {
        bed.download("1.0.0-alpha15")
        val starting = starting(bed.launch(pid = 101))
        completeStart(101, starting)
        val completed = state()

        starting.startup.hostFailed()
        starting.startup.shutDownWithoutFailure()

        assertEquals(completed, state())
        assertEquals(versions("1.0.0-alpha15"), completed.completedStartVersions)
        assertEquals(emptyMap(), completed.failedStartCounts)
    }

    @Test
    fun `does not record a completed start when the window ends after the host failed or the JVM began to shut down`() {
        bed.download("1.0.0-alpha15")
        val failed = starting(bed.launch(pid = 101))
        failed.startup.hostFailed()
        bed.watchWindow(failed, publishedBy = 101)
        bed.crash(101)
        val quit = starting(bed.launch(pid = 102))
        quit.startup.shutDownWithoutFailure()
        bed.watchWindow(quit, publishedBy = 102)

        assertEquals(emptySet(), state().completedStartVersions)
        assertEquals(mapOf(hostVersion("1.0.0-alpha15") to 1), state().failedStartCounts)
    }

    @Test
    fun `forgets the failed starts of a version once it completes one`() {
        bed.download("1.0.0-alpha15")
        bed.launch(pid = 101)
        bed.crash(101)

        completeStart(102, starting(bed.launch(pid = 102)))

        assertEquals(emptyMap(), state().failedStartCounts)
    }

    @Test
    fun `does not count a failed start of a version that has completed one before`() {
        bed.download("1.0.0-alpha15")
        writeState(completedStartVersions = versions("1.0.0-alpha15"))
        bed.launch(pid = 101)
        bed.crash(101)
        bed.launch(pid = 102)
        bed.crash(102)

        assertEquals("1.0.0-alpha15", starting(bed.launch(pid = 103)).start.version.name)
        assertEquals(emptyMap(), state().failedStartCounts)
        assertEquals(emptySet(), state().setAsideVersions)
    }

    @Test
    fun `leaves a start alone while its process runs`() {
        bed.download("1.0.0-alpha15")
        bed.hostComesUp(101, starting(bed.launch(pid = 101)))

        assertEquals(LaunchOutcome.BroughtRunningHostToFront, bed.launch(pid = 102))
        assertEquals(101L, state().startingHost?.pid)
        assertEquals(emptyMap(), state().failedStartCounts)
    }

    @Test
    fun `counts a start whose process ID another process has taken since as failed`() {
        bed.download("1.0.0-alpha15")
        bed.runOtherProcess(101)
        writeState(startingHost = StartingHost(hostVersion("1.0.0-alpha15"), pid = 101, processStartMillis = 5))

        bed.launch(pid = 102)

        assertEquals(mapOf(hostVersion("1.0.0-alpha15") to 1), state().failedStartCounts)
    }

    @Test
    fun `never sets the bundled version aside`() {
        bed.launch(pid = 101)
        bed.crash(101)
        bed.launch(pid = 102)
        bed.crash(102)

        val again = starting(bed.launch(pid = 103))

        assertEquals("1.0.0-alpha13", again.start.version.name)
        assertEquals(emptySet(), state().setAsideVersions)
    }

    @Test
    fun `has nothing left without a bundled version once every download is set aside`() {
        bed.download("1.0.0-alpha15")
        writeState(setAsideVersions = versions("1.0.0-alpha15"))

        val outcome = bed.launcher(pid = 101, bundled = false).launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.NothingLeft, outcome)
        assertFalse(bed.lockFiles.isHeld(hostVersionsDirectory.launchLockFile))
        assertNull(state().startingHost)
    }

    @Test
    fun `keeps the running version and the newest newer one after a start`() {
        listOf("1.0.0-alpha14", "1.0.0-alpha15", "1.0.0-alpha16", "1.0.0-alpha17").forEach(bed::download)
        writeState(
            completedStartVersions = versions("1.0.0-alpha14"),
            setAsideVersions = versions("1.0.0-alpha15", "1.0.0-alpha16", "1.0.0-alpha17"),
            failedStartCounts = mapOf(hostVersion("1.0.0-alpha16") to 2, hostVersion("1.0.0-alpha17") to 2),
        )

        val starting = starting(bed.launch(pid = 101))
        completeStart(101, starting)

        assertEquals("1.0.0-alpha14", starting.start.version.name)
        assertEquals(listOf("1.0.0-alpha17", "1.0.0-alpha14"), hostVersionsDirectory.installedVersions().map { it.version.name })
        assertEquals(
            LauncherState(
                completedStartVersions = versions("1.0.0-alpha14"),
                setAsideVersions = versions("1.0.0-alpha17"),
                failedStartCounts = mapOf(hostVersion("1.0.0-alpha17") to 2),
                startingHost = null,
            ),
            state(),
        )
        assertTrue(Files.exists(bed.bundledDirectory.resolve("release.json")), "the bundled version is never deleted")
    }

    @Test
    fun `asks a running host to come forward instead of starting another`() {
        bed.download("1.0.0-alpha15")
        bed.lockFiles.of(42).lock(hostVersionsDirectory.instanceLockFile)

        assertEquals(LaunchOutcome.BroughtRunningHostToFront, bed.launch(pid = 101))
        assertEquals(1, bed.runningHostChannel.bringToFrontRequests)
        assertNull(state().startingHost)
        assertFalse(bed.lockFiles.isHeld(hostVersionsDirectory.launchLockFile))
    }

    @Test
    fun `reports a running host that does not answer`() {
        bed.lockFiles.of(42).lock(hostVersionsDirectory.instanceLockFile)

        val outcome = bed.launcher(pid = 101, runningHostChannel = FakeRunningHostChannel(hostVersionsDirectory, answers = false)).launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.RunningHostUnreachable, outcome)
        assertNull(state().startingHost)
    }

    @Test
    fun `waits for a host restarting within its window to end, without launch lock, and does not count that start as failed`() {
        bed.download("1.0.0-alpha15")
        val restarting = starting(bed.launch(pid = 101))
        bed.hostComesUp(101, restarting)
        val waitedFor = CopyOnWriteArrayList<Long>()
        val launcher = bed.launcher(pid = 102, onAwaitExit = { pid ->
            assertNotNull(bed.lockFiles.of(7).tryLock(hostVersionsDirectory.launchLockFile), "it waits without launch.lock").close()
            waitedFor += pid
            bed.shutDown(101, restarting)
        })

        val restarted = starting(launcher.launch(afterPid = 101, retryVersion = null))

        assertEquals(listOf(101L), waitedFor)
        assertEquals("1.0.0-alpha15", restarted.start.version.name)
        assertEquals(emptyMap(), state().failedStartCounts)
        assertEquals(102L, state().startingHost?.pid)
    }

    @Test
    fun `starts one host when two launchers start at once`() {
        bed.download("1.0.0-alpha15")
        val outcomes = LinkedBlockingQueue<Pair<Long, LaunchOutcome>>()

        val launches = listOf(101L, 102L).map { pid ->
            val launcher = bed.launcher(pid = pid)
            thread(isDaemon = true) { outcomes += pid to launcher.launch(afterPid = null, retryVersion = null) }
        }
        val (firstPid, first) = assertNotNull(outcomes.poll(10, TimeUnit.SECONDS))
        bed.hostComesUp(firstPid, starting(first))
        launches.forEach { it.join(10_000) }

        assertEquals(LaunchOutcome.BroughtRunningHostToFront, assertNotNull(outcomes.poll()).second)
        assertEquals(firstPid, state().startingHost?.pid)
    }

    @Test
    fun `tries a set-aside version again when asked to`() {
        bed.download("1.0.0-alpha14")
        bed.download("1.0.0-alpha15")
        writeState(
            completedStartVersions = versions("1.0.0-alpha14"),
            setAsideVersions = versions("1.0.0-alpha15"),
            failedStartCounts = mapOf(hostVersion("1.0.0-alpha15") to 2),
        )

        val starting = starting(bed.launch(pid = 101, retryVersion = hostVersion("1.0.0-alpha15")))

        assertEquals("1.0.0-alpha15", starting.start.version.name)
        assertEquals(emptySet(), state().setAsideVersions)
        assertEquals(emptyMap(), state().failedStartCounts)
    }

    @Test
    fun `keeps a change made to the record while the host ran when it records the completed start`() {
        bed.download("1.0.0-alpha15")
        bed.download("1.0.0-alpha16")
        writeState(setAsideVersions = versions("1.0.0-alpha16"))
        val starting = starting(bed.launch(pid = 101))
        bed.hostComesUp(101, starting)
        hostVersionsDirectory.writeLauncherState(state().copy(setAsideVersions = emptySet()))

        bed.watchWindow(starting, publishedBy = 101)

        assertEquals(emptySet(), state().setAsideVersions)
        assertEquals(versions("1.0.0-alpha15"), state().completedStartVersions)
    }

    @Test
    fun `does not take the instance JSON an earlier host left behind for this host's`() {
        InstanceJson.publish(hostVersionsDirectory, InstanceJson(port = 0, pid = 101, token = "stale"))

        bed.launch(pid = 101)

        assertFalse(bed.runningHostChannel.isInstanceJsonPublishedBy(101))
    }

    private fun starting(outcome: LaunchOutcome): LaunchOutcome.Starting = assertIs<LaunchOutcome.Starting>(outcome)

    /** The host of the process [pid] comes up and is still running when its window ends. */
    private fun completeStart(pid: Long, starting: LaunchOutcome.Starting) {
        bed.hostComesUp(pid, starting)
        bed.watchWindow(starting, publishedBy = pid)
    }

    private fun state(): LauncherState = hostVersionsDirectory.readLauncherState()

    private fun writeState(
        completedStartVersions: Set<HostVersion> = emptySet(),
        setAsideVersions: Set<HostVersion> = emptySet(),
        failedStartCounts: Map<HostVersion, Int> = emptyMap(),
        startingHost: StartingHost? = null,
    ) {
        hostVersionsDirectory.writeLauncherState(LauncherState(completedStartVersions, setAsideVersions, failedStartCounts, startingHost))
    }

    private fun versions(vararg names: String): Set<HostVersion> = names.mapTo(LinkedHashSet(), ::hostVersion)

    private fun recordingLaunchLock(lockFiles: LockFiles, events: MutableList<String>) = object : LockFiles by lockFiles {
        override fun lock(path: Path): HeldLock {
            val held = lockFiles.lock(path)
            if (path == hostVersionsDirectory.launchLockFile) events += "lock"
            return HeldLock {
                if (path == hostVersionsDirectory.launchLockFile) events += "release"
                held.close()
            }
        }
    }
}
