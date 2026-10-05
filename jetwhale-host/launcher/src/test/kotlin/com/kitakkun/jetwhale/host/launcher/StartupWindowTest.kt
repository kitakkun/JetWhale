package com.kitakkun.jetwhale.host.launcher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class StartupWindowTest {
    private val bed = LaunchTestBed()
    private val time = TestTimeSource()
    private val launchLockHeldAtPolls = mutableListOf<Boolean>()
    private val window = StartupWindow(length = 30.seconds, pollInterval = 200.milliseconds, timeSource = time) {
        launchLockHeldAtPolls += bed.lockFiles.isHeld(bed.hostVersionsDirectory.launchLockFile)
        time += it
    }

    @Test
    fun `lets go of launch lock once the host has published, and records a completed start at the end of the window`() {
        val starting = assertIs<LaunchOutcome.Starting>(bed.launch(pid = 101))
        var polls = 0

        window.watch(starting.startup) { polls++ >= 2 }

        assertEquals(150, launchLockHeldAtPolls.size)
        assertEquals(listOf(true, true, false, false), launchLockHeldAtPolls.take(4))
        assertTrue(starting.start.version in bed.hostVersionsDirectory.readLauncherState().completedStartVersions)
        assertNull(bed.hostVersionsDirectory.readLauncherState().startingHost)
    }

    @Test
    fun `counts the window from when the watch starts`() {
        time += 1.seconds * 3600
        val starting = assertIs<LaunchOutcome.Starting>(bed.launch(pid = 101))

        window.watch(starting.startup) { false }

        assertEquals(150, launchLockHeldAtPolls.size)
        assertTrue(launchLockHeldAtPolls.all { it }, "a host that never publishes keeps launch.lock to the end of its window")
    }
}
