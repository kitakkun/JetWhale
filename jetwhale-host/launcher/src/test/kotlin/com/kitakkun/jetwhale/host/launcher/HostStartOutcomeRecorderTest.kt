package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HeldLock
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.LauncherState
import com.kitakkun.jetwhale.host.release.LockFiles
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class HostStartOutcomeRecorderTest {
    private val bed = LaunchTestBed()
    private val hostDirectory = bed.hostDirectory

    @Test
    fun `holds launch lock until the host publishes its instance JSON, and takes it again to record the completed start`() {
        bed.download("1.0.0-alpha15")
        val events = CopyOnWriteArrayList<String>()

        val launcher = bed.launcher(pid = 101, lockFiles = recordingLaunchLock(bed.lockFiles.of(101), events))
        val starting = starting(launcher.launch(afterPid = null, retryVersion = null))
        events += "launched"
        bed.hostComesUp(101, starting)
        events += "published"
        starting.startOutcomeRecorder.recordCompletedStartAfter(30.seconds)

        assertEquals(listOf("lock", "launched", "release", "published", "lock", "release"), events)
        assertEquals(hostVersions("1.0.0-alpha15"), state().completedStartVersions)
        assertNull(state().startedHostProcess)
    }

    @Test
    fun `records a completed start once the startup time window has passed, and not before`() {
        bed.download("1.0.0-alpha15")
        val completedStartVersionsAtSleeps = mutableListOf<Pair<Duration, Set<HostVersion>>>()
        val launcher = bed.launcher(pid = 101, sleep = { completedStartVersionsAtSleeps += it to state().completedStartVersions })
        val starting = starting(launcher.launch(afterPid = null, retryVersion = null))

        starting.startOutcomeRecorder.recordCompletedStartAfter(30.seconds)

        assertEquals(listOf(30.seconds to emptySet()), completedStartVersionsAtSleeps)
        assertEquals(hostVersions("1.0.0-alpha15"), state().completedStartVersions)
    }

    @Test
    fun `counts a throw from the host's main as a failed start, and lets go of launch lock`() {
        bed.download("1.0.0-alpha15")

        starting(bed.launch(pid = 101)).startOutcomeRecorder.recordFailedStart()

        assertEquals(mapOf(hostVersion("1.0.0-alpha15") to 1), state().failedStartCounts)
        assertNull(state().startedHostProcess)
        assertFalse(bed.lockFiles.isHeld(hostDirectory.launchLockFile))
    }

    @Test
    fun `counts a shutdown within the startup time window as neither failed nor completed, before the host has published or after`() {
        bed.download("1.0.0-alpha15")
        bed.shutDown(101, starting(bed.launch(pid = 101)))
        val published = starting(bed.launch(pid = 102))
        bed.hostComesUp(102, published)
        bed.shutDown(102, published)

        val again = starting(bed.launch(pid = 103))

        assertEquals("1.0.0-alpha15", again.chosenHostJar.version.name)
        assertEquals(emptyMap(), state().failedStartCounts)
        assertEquals(emptySet(), state().setAsideVersions)
        assertEquals(emptySet(), state().completedStartVersions)
    }

    @Test
    fun `records nothing more when the host fails or the JVM shuts down after a completed start`() {
        bed.download("1.0.0-alpha15")
        val starting = starting(bed.launch(pid = 101))
        bed.completeStart(101, starting)
        val completed = state()

        starting.startOutcomeRecorder.recordFailedStart()
        starting.startOutcomeRecorder.recordStartEndedWithoutFailure()

        assertEquals(completed, state())
        assertEquals(hostVersions("1.0.0-alpha15"), completed.completedStartVersions)
        assertEquals(emptyMap(), completed.failedStartCounts)
    }

    @Test
    fun `does not record a completed start when the startup time window ends after the host failed or the JVM began to shut down`() {
        bed.download("1.0.0-alpha15")
        val failed = starting(bed.launch(pid = 101))
        failed.startOutcomeRecorder.recordFailedStart()
        failed.startOutcomeRecorder.recordCompletedStartAfter(30.seconds)
        bed.crash(101)
        val quit = starting(bed.launch(pid = 102))
        quit.startOutcomeRecorder.recordStartEndedWithoutFailure()
        quit.startOutcomeRecorder.recordCompletedStartAfter(30.seconds)

        assertEquals(emptySet(), state().completedStartVersions)
        assertEquals(mapOf(hostVersion("1.0.0-alpha15") to 1), state().failedStartCounts)
    }

    @Test
    fun `forgets the failed starts of a version once it completes one`() {
        bed.download("1.0.0-alpha15")
        bed.launch(pid = 101)
        bed.crash(101)

        bed.completeStart(102, starting(bed.launch(pid = 102)))

        assertEquals(emptyMap(), state().failedStartCounts)
    }

    @Test
    fun `deletes the downloaded versions that do not stay once it records a completed start`() {
        bed.download("1.0.0-alpha14")
        bed.download("1.0.0-alpha15")
        bed.writeLauncherState(completedStartVersions = hostVersions("1.0.0-alpha14"))

        bed.completeStart(101, starting(bed.launch(pid = 101)))

        assertEquals(listOf("1.0.0-alpha15"), hostDirectory.hostVersionDirectories().map { it.version.name })
        assertEquals(hostVersions("1.0.0-alpha15"), state().completedStartVersions)
    }

    @Test
    fun `keeps a change made to the record while the host ran when it records the completed start`() {
        bed.download("1.0.0-alpha15")
        bed.download("1.0.0-alpha16")
        bed.writeLauncherState(setAsideVersions = hostVersions("1.0.0-alpha16"))
        val starting = starting(bed.launch(pid = 101))
        bed.hostComesUp(101, starting)
        hostDirectory.writeLauncherState(state().copy(setAsideVersions = emptySet()))

        starting.startOutcomeRecorder.recordCompletedStartAfter(30.seconds)

        assertEquals(emptySet(), state().setAsideVersions)
        assertEquals(hostVersions("1.0.0-alpha15"), state().completedStartVersions)
    }

    private fun starting(outcome: LaunchOutcome): LaunchOutcome.Starting = assertIs<LaunchOutcome.Starting>(outcome)

    private fun state(): LauncherState = hostDirectory.readLauncherState()

    private fun recordingLaunchLock(lockFiles: LockFiles, events: MutableList<String>) = object : LockFiles by lockFiles {
        override fun lock(path: Path): HeldLock {
            val held = lockFiles.lock(path)
            if (path == hostDirectory.launchLockFile) events += "lock"
            return HeldLock {
                if (path == hostDirectory.launchLockFile) events += "release"
                held.close()
            }
        }
    }
}
