package com.kitakkun.jetwhale.host.launcher

import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/** The processes a launch deals with: its own, which runs the host it chooses, and those of earlier launches. */
interface ProcessTable {
    val currentPid: Long

    /** When this process started, in milliseconds since the epoch; null when the OS does not say. */
    val currentStartMillis: Long?

    /**
     * Whether the process [pid] that started at [startMillis] still runs. A later process that got the
     * same ID is not it.
     */
    fun isRunning(pid: Long, startMillis: Long?): Boolean

    /** Waits, within reason, for the process [pid] to end. */
    fun awaitExit(pid: Long)
}

/** This machine's processes, through [ProcessHandle]. */
class OsProcessTable(private val exitTimeout: Duration) : ProcessTable {
    override val currentPid: Long = ProcessHandle.current().pid()

    override val currentStartMillis: Long? = ProcessHandle.current().startMillis()

    override fun isRunning(pid: Long, startMillis: Long?): Boolean {
        val process = ProcessHandle.of(pid).orElse(null)?.takeIf(ProcessHandle::isAlive) ?: return false
        val actualStartMillis = process.startMillis()
        // Without both start times a reused ID cannot be told apart, and a host still in its
        // startup time window must not be judged ended, so a live process with the ID counts as it.
        return startMillis == null || actualStartMillis == null || actualStartMillis == startMillis
    }

    override fun awaitExit(pid: Long) {
        ProcessHandle.of(pid).ifPresent { process ->
            process.onExit().completeOnTimeout(process, exitTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS).join()
        }
    }
}

private fun ProcessHandle.startMillis(): Long? = info().startInstant().map(Instant::toEpochMilli).orElse(null)
