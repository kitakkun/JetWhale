package com.kitakkun.jetwhale.host.launcher

import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * The time after a host's launch in which an exit counts against the start. It is the startup grace
 * of the host's crash recovery, so the launcher and the host count the same crashes as startup
 * crashes.
 */
class StartupWindow(
    private val length: Duration,
    private val pollInterval: Duration,
    private val timeSource: TimeSource,
    private val sleep: (Duration) -> Unit,
) {
    /**
     * Watches [process] from now, which is right after it started, until it exits or the window
     * ends. [whileRunning] runs on every poll while the process is alive.
     *
     * @return The exit status when the process exited within the window, or null when it was still
     * running at the end of it.
     */
    fun watch(process: HostProcess, whileRunning: () -> Unit): Int? {
        val started = timeSource.markNow()
        while (true) {
            val exitStatus = process.exitStatus()
            if (exitStatus != null) return exitStatus
            if (started.elapsedNow() >= length) return null
            whileRunning()
            sleep(pollInterval)
        }
    }
}

/** How the launcher judges one start of a host. */
sealed interface StartupOutcome {
    /** Still running at the end of the startup window. */
    data object Completed : StartupOutcome

    /**
     * Neither failed nor completed: the host exited normally after it published its instance record,
     * as it does when the user quits or restarts it, or it exited before publishing because another
     * host holds the instance and was asked to come forward instead.
     */
    data object Neither : StartupOutcome

    /**
     * Exited within the window before it published its instance record, whatever its status, or
     * after publishing with a non-zero status or by a signal.
     */
    data class Failed(val exitStatus: Int) : StartupOutcome
}
