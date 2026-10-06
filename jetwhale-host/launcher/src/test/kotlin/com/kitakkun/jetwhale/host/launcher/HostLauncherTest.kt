package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HeldLock
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataReader
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.InstanceJson
import com.kitakkun.jetwhale.host.release.LauncherState
import com.kitakkun.jetwhale.host.release.LockFiles
import com.kitakkun.jetwhale.host.release.StartedHostProcess
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class HostLauncherTest {
    private val bed = LaunchTestBed()
    private val hostDirectory = bed.hostDirectory

    @Test
    fun `starts the newest downloaded version that verifies and writes its start down`() {
        bed.download("1.0.0-alpha14")
        bed.download("1.0.0-alpha15")

        val starting = starting(bed.launch(pid = 101))

        assertEquals("1.0.0-alpha15", starting.chosenHostJar.version.name)
        assertNull(starting.setAsideVersion)
        assertEquals(StartedHostProcess(hostVersion("1.0.0-alpha15"), pid = 101, processStartMillis = 101_000), state().startedHostProcess)
    }

    @Test
    fun `starts the bundled version when no download is newer`() {
        bed.download("1.0.0-alpha12")

        val starting = starting(bed.launch(pid = 101))
        bed.completeStart(101, starting)

        assertEquals("1.0.0-alpha13", starting.chosenHostJar.version.name)
        assertTrue(starting.chosenHostJar.isBundled)
        assertFalse(bed.versionDirectory("1.0.0-alpha12").exists(), "a version older than the bundled one goes")
    }

    @Test
    fun `deletes a version whose jar does not match its metadata and starts the next`() {
        bed.download("1.0.0-alpha14")
        bed.download("1.0.0-alpha15")
        Files.write(bed.versionDirectory("1.0.0-alpha15").resolve(hostJarName(hostVersion("1.0.0-alpha15"), PLATFORM)), "tampered!!!!!!!!".toByteArray())

        assertEquals("1.0.0-alpha14", starting(bed.launch(pid = 101)).chosenHostJar.version.name)
        assertFalse(bed.versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `deletes a version whose metadata does not read, or names another version`() {
        bed.download("1.0.0-alpha15")
        Files.writeString(bed.versionDirectory("1.0.0-alpha15").resolve("release.json"), "{ not json")
        writeHostVersion(bed.versionDirectory("1.0.0-alpha16"), "1.0.0-alpha16") { it.copy(version = hostVersion("1.0.0-alpha17")) }

        assertEquals("1.0.0-alpha13", starting(bed.launch(pid = 101)).chosenHostJar.version.name)
        assertFalse(bed.versionDirectory("1.0.0-alpha15").exists())
        assertFalse(bed.versionDirectory("1.0.0-alpha16").exists())
    }

    @Test
    fun `deletes a version that its signature verifier does not trust`() {
        bed.download("1.0.0-alpha15")

        val outcome = bed.launcher(pid = 101, metadataReader = HostReleaseMetadataReader { _, _ -> false }).launch(afterPid = null, retryVersion = null)

        assertEquals("1.0.0-alpha13", starting(outcome).chosenHostJar.version.name)
        assertFalse(bed.versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `deletes a version directory that links outside the host directory, but not what it links to`() {
        val elsewhere = bed.appData.resolve("elsewhere/1.0.0-alpha15")
        writeHostVersion(elsewhere, "1.0.0-alpha15") { it }
        Files.createDirectories(hostDirectory.root)
        Files.createSymbolicLink(bed.versionDirectory("1.0.0-alpha15"), elsewhere)

        assertEquals("1.0.0-alpha13", starting(bed.launch(pid = 101)).chosenHostJar.version.name)
        assertFalse(Files.exists(bed.versionDirectory("1.0.0-alpha15"), LinkOption.NOFOLLOW_LINKS))
        assertTrue(Files.exists(elsewhere.resolve("release.json")))
    }

    @Test
    fun `deletes a version whose jar cannot be read and starts the next`() {
        bed.download("1.0.0-alpha15")
        val jar = bed.versionDirectory("1.0.0-alpha15").resolve(hostJarName(hostVersion("1.0.0-alpha15"), PLATFORM)).toFile()
        Assume.assumeTrue("the file system cannot take read permission away", jar.setReadable(false) && !jar.canRead())

        assertEquals("1.0.0-alpha13", starting(bed.launch(pid = 101)).chosenHostJar.version.name)
        assertFalse(bed.versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `keeps but skips a version it refuses`() {
        bed.download("1.0.0-alpha14")
        writeHostVersion(bed.versionDirectory("1.0.0-alpha15"), "1.0.0-alpha15") { it.copy(launcherContract = 2) }

        assertEquals("1.0.0-alpha14", starting(bed.launch(pid = 101)).chosenHostJar.version.name)
        assertTrue(bed.versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `keeps but skips a version that needs a JVM argument this JVM did not start with`() {
        bed.download("1.0.0-alpha14")
        writeHostVersion(bed.versionDirectory("1.0.0-alpha15"), "1.0.0-alpha15") { it.copy(jvmArgs = it.jvmArgs + "--enable-native-access=ALL-UNNAMED") }

        assertEquals("1.0.0-alpha14", starting(bed.launch(pid = 101)).chosenHostJar.version.name)
        assertTrue(bed.versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `skips a set-aside version`() {
        bed.download("1.0.0-alpha14")
        bed.download("1.0.0-alpha15")
        bed.writeLauncherState(setAsideVersions = hostVersions("1.0.0-alpha15"))

        assertEquals("1.0.0-alpha14", starting(bed.launch(pid = 101)).chosenHostJar.version.name)
        assertTrue(bed.versionDirectory("1.0.0-alpha15").exists(), "the user can still try it again")
        assertEquals(hostVersions("1.0.0-alpha15"), state().setAsideVersions)
    }

    @Test
    fun `counts a start whose process ended within its startup time window without a record as failed, and starts the version again`() {
        bed.download("1.0.0-alpha14")
        bed.download("1.0.0-alpha15")
        bed.hostComesUp(101, starting(bed.launch(pid = 101)))
        bed.crash(101)

        val again = starting(bed.launch(pid = 102))

        assertEquals("1.0.0-alpha15", again.chosenHostJar.version.name)
        assertNull(again.setAsideVersion)
        assertEquals(mapOf(hostVersion("1.0.0-alpha15") to 1), state().failedStartCounts)
        assertEquals(102L, state().startedHostProcess?.pid)
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

        assertEquals("1.0.0-alpha14", fallback.chosenHostJar.version.name)
        assertEquals("1.0.0-alpha15", fallback.setAsideVersion?.name)
        assertEquals(hostVersions("1.0.0-alpha15"), state().setAsideVersions)
        assertTrue(bed.versionDirectory("1.0.0-alpha15").exists())
    }

    @Test
    fun `sets aside a version after a crash and a throw from its main`() {
        bed.download("1.0.0-alpha15")
        bed.launch(pid = 101)
        bed.crash(101)
        starting(bed.launch(pid = 102)).startOutcomeRecorder.recordFailedStart()
        bed.crash(102)

        val fallback = starting(bed.launch(pid = 103))

        assertEquals("1.0.0-alpha13", fallback.chosenHostJar.version.name)
        assertEquals("1.0.0-alpha15", fallback.setAsideVersion?.name)
    }

    @Test
    fun `does not count a failed start of a version that has completed one before`() {
        bed.download("1.0.0-alpha15")
        bed.writeLauncherState(completedStartVersions = hostVersions("1.0.0-alpha15"))
        bed.launch(pid = 101)
        bed.crash(101)
        bed.launch(pid = 102)
        bed.crash(102)

        assertEquals("1.0.0-alpha15", starting(bed.launch(pid = 103)).chosenHostJar.version.name)
        assertEquals(emptyMap(), state().failedStartCounts)
        assertEquals(emptySet(), state().setAsideVersions)
    }

    @Test
    fun `leaves a start alone while its process runs`() {
        bed.download("1.0.0-alpha15")
        bed.hostComesUp(101, starting(bed.launch(pid = 101)))

        assertEquals(LaunchOutcome.BroughtRunningHostToFront, bed.launch(pid = 102))
        assertEquals(101L, state().startedHostProcess?.pid)
        assertEquals(emptyMap(), state().failedStartCounts)
    }

    @Test
    fun `counts a start whose process ID another process has taken since as failed`() {
        bed.download("1.0.0-alpha15")
        bed.runOtherProcess(101)
        bed.writeLauncherState(startedHostProcess = StartedHostProcess(hostVersion("1.0.0-alpha15"), pid = 101, processStartMillis = 5))

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

        assertEquals("1.0.0-alpha13", again.chosenHostJar.version.name)
        assertEquals(emptySet(), state().setAsideVersions)
    }

    @Test
    fun `has nothing left without a bundled version once every download is set aside`() {
        bed.download("1.0.0-alpha15")
        bed.writeLauncherState(setAsideVersions = hostVersions("1.0.0-alpha15"))

        val outcome = bed.launcher(pid = 101, bundled = false).launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.NothingLeft, outcome)
        assertFalse(bed.lockFiles.isHeld(hostDirectory.launchLockFile))
        assertNull(state().startedHostProcess)
    }

    @Test
    fun `asks a running host to come forward instead of starting another`() {
        bed.download("1.0.0-alpha15")
        bed.lockFiles.of(42).lock(hostDirectory.instanceLockFile)

        assertEquals(LaunchOutcome.BroughtRunningHostToFront, bed.launch(pid = 101))
        assertEquals(1, bed.runningHostChannel.bringToFrontRequests)
        assertNull(state().startedHostProcess)
        assertFalse(bed.lockFiles.isHeld(hostDirectory.launchLockFile))
    }

    @Test
    fun `reports a running host that does not answer`() {
        bed.lockFiles.of(42).lock(hostDirectory.instanceLockFile)

        val outcome = bed.launcher(pid = 101, runningHostChannel = FakeRunningHostChannel(hostDirectory, answers = false)).launch(afterPid = null, retryVersion = null)

        assertEquals(LaunchOutcome.RunningHostUnreachable, outcome)
        assertNull(state().startedHostProcess)
    }

    @Test
    fun `waits for a host restarting within its startup time window to end, without launch lock, and does not count that start as failed`() {
        bed.download("1.0.0-alpha15")
        val restarting = starting(bed.launch(pid = 101))
        bed.hostComesUp(101, restarting)
        val waitedFor = CopyOnWriteArrayList<Long>()
        val launcher = bed.launcher(pid = 102, onAwaitExit = { pid ->
            assertNotNull(bed.lockFiles.of(7).tryLock(hostDirectory.launchLockFile), "it waits without launch.lock").close()
            waitedFor += pid
            bed.shutDown(101, restarting)
        })

        val restarted = starting(launcher.launch(afterPid = 101, retryVersion = null))

        assertEquals(listOf(101L), waitedFor)
        assertEquals("1.0.0-alpha15", restarted.chosenHostJar.version.name)
        assertEquals(emptyMap(), state().failedStartCounts)
        assertEquals(102L, state().startedHostProcess?.pid)
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
        assertEquals(firstPid, state().startedHostProcess?.pid)
    }

    @Test
    fun `tries a set-aside version again when asked to`() {
        bed.download("1.0.0-alpha14")
        bed.download("1.0.0-alpha15")
        bed.writeLauncherState(
            completedStartVersions = hostVersions("1.0.0-alpha14"),
            setAsideVersions = hostVersions("1.0.0-alpha15"),
            failedStartCounts = mapOf(hostVersion("1.0.0-alpha15") to 2),
        )

        val starting = starting(bed.launch(pid = 101, retryVersion = hostVersion("1.0.0-alpha15")))

        assertEquals("1.0.0-alpha15", starting.chosenHostJar.version.name)
        assertEquals(emptySet(), state().setAsideVersions)
        assertEquals(emptyMap(), state().failedStartCounts)
    }

    @Test
    fun `does not take the instance JSON an earlier host left behind for this host's`() {
        hostDirectory.publishInstanceJson(InstanceJson(port = 0, pid = 101, token = "stale"))

        bed.launch(pid = 101)

        assertFalse(bed.runningHostChannel.isInstanceJsonPublishedBy(101))
    }

    private fun starting(outcome: LaunchOutcome): LaunchOutcome.Starting = assertIs<LaunchOutcome.Starting>(outcome)

    private fun state(): LauncherState = hostDirectory.readLauncherState()
}
