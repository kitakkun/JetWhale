package com.kitakkun.jetwhale.host.launcher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class InstanceJsonPublicationWatcherTest {
    private val bed = LaunchTestBed()
    private val time = TestTimeSource()
    private val launchLockHeldAtPolls = mutableListOf<Boolean>()

    @Test
    fun `lets go of launch lock once the host has published, and stops polling`() {
        val starting = assertIs<LaunchOutcome.Starting>(bed.launch(pid = 101))

        watcher { launchLockHeldAtPolls.size >= 3 }.releaseLaunchLockWhenInstanceJsonIsPublished(starting.hostStart)

        assertEquals(listOf(true, true, true), launchLockHeldAtPolls)
        assertFalse(bed.lockFiles.isHeld(bed.hostVersionsDirectory.launchLockFile))
        assertNotNull(bed.hostVersionsDirectory.readLauncherState().startInProgress, "the start is still to be judged")
    }

    @Test
    fun `stops polling at the end of the startup time window, counted from the watch, when the host never publishes`() {
        time += 1.hours
        val starting = assertIs<LaunchOutcome.Starting>(bed.launch(pid = 101))

        watcher { false }.releaseLaunchLockWhenInstanceJsonIsPublished(starting.hostStart)

        assertEquals(150, launchLockHeldAtPolls.size)
        assertTrue(launchLockHeldAtPolls.all { it })
        assertTrue(bed.lockFiles.isHeld(bed.hostVersionsDirectory.launchLockFile), "launch.lock stays for the end of the startup time window to let go")
    }

    private fun watcher(isInstanceJsonPublished: () -> Boolean) = InstanceJsonPublicationWatcher(
        startupTimeWindow = 30.seconds,
        pollInterval = 200.milliseconds,
        timeSource = time,
        sleep = time::plusAssign,
        isInstanceJsonPublished = {
            launchLockHeldAtPolls += bed.lockFiles.isHeld(bed.hostVersionsDirectory.launchLockFile)
            isInstanceJsonPublished()
        },
    )
}
