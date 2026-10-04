package com.kitakkun.jetwhale.host.launcher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class StartupWindowTest {
    private val time = TestTimeSource()
    private val window = StartupWindow(length = 30.seconds, pollInterval = 200.milliseconds, timeSource = time, sleep = time::plusAssign)

    @Test
    fun `reports no status for a host still running at the end of the window`() {
        var polls = 0

        val outcome = window.watch(ProcessExitingAt(exitAfter = 31.seconds, status = 1)) { polls++ }

        assertEquals(null, outcome)
        assertEquals(150, polls)
    }

    @Test
    fun `reports the status of a host that exits within the window`() {
        assertEquals(134, window.watch(ProcessExitingAt(exitAfter = 29.seconds, status = 134)) {})
        assertEquals(0, window.watch(ProcessExitingAt(exitAfter = 1.seconds, status = 0)) {})
    }

    @Test
    fun `counts the window from when the watch starts`() {
        time += 1.seconds * 3600

        assertEquals(1, window.watch(ProcessExitingAt(exitAfter = 10.seconds, status = 1)) {})
    }

    private inner class ProcessExitingAt(exitAfter: Duration, private val status: Int) : HostProcess {
        private val exitsAt = time.markNow() + exitAfter

        override val pid: Long = 1

        override fun exitStatus(): Int? = if (exitsAt.hasPassedNow()) status else null

        override fun waitForExit(): Int = status
    }
}
