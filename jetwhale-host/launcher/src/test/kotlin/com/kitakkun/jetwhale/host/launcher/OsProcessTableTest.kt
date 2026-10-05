package com.kitakkun.jetwhale.host.launcher

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class OsProcessTableTest {
    private val processes = OsProcessTable(exitTimeout = 10.seconds)

    @Test
    fun `tells this process from a later one that got its ID`() {
        val startMillis = assertNotNull(processes.currentStartMillis)

        assertTrue(processes.isRunning(processes.currentPid, startMillis))
        assertTrue(processes.isRunning(processes.currentPid, startMillis = null))
        assertFalse(processes.isRunning(processes.currentPid, startMillis + 1))
    }

    @Test
    fun `does not take a process that has ended for a running one`() {
        val ended = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-version").start()
        assertTrue(ended.waitFor(60, TimeUnit.SECONDS))

        processes.awaitExit(ended.pid())

        assertFalse(processes.isRunning(ended.pid(), startMillis = null))
    }
}
