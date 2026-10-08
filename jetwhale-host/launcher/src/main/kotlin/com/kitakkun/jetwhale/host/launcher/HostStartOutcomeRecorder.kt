package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HeldLock
import com.kitakkun.jetwhale.host.release.HostDirectory
import com.kitakkun.jetwhale.host.release.LauncherState
import com.kitakkun.jetwhale.host.release.LockFiles
import kotlin.time.Duration

/**
 * Records how the start of [chosenHostJar] in this process ends. The first of three ends counts, and
 * is recorded once, under `launch.lock`: the end of the startup time window, a failure of the host's
 * main, or a shutdown of this JVM. Until then it holds the `launch.lock` the launch took, unless it
 * has been told to let it go.
 *
 * @param launchLock The `launch.lock` the launch took, which this recorder holds from now on.
 */
class HostStartOutcomeRecorder internal constructor(
    private val chosenHostJar: ChosenHostJar,
    launchLock: HeldLock,
    private val hostDirectory: HostDirectory,
    private val lockFiles: LockFiles,
    private val hostVersionRetention: HostVersionRetention,
    private val failedStartCounter: FailedStartCounter,
    private val log: LauncherLog,
    private val sleep: (Duration) -> Unit,
) {
    private var heldLaunchLock: HeldLock? = launchLock

    private var isOutcomeRecorded = false

    /** Lets `launch.lock` go until the outcome is recorded, which takes it again for the record. */
    @Synchronized
    fun releaseLaunchLock() {
        heldLaunchLock?.close()
        heldLaunchLock = null
    }

    /**
     * Waits out [startupTimeWindow] from now, which is right before the host's main is called, and
     * then records a completed start, unless the start has ended otherwise by then.
     */
    fun recordCompletedStartAfter(startupTimeWindow: Duration) {
        sleep(startupTimeWindow)
        recordCompletedStart()
    }

    /** The host still runs at the end of its startup time window: it completed its start. */
    @Synchronized
    private fun recordCompletedStart() = recordOutcomeOnce { state ->
        log.write("${chosenHostJar.version} completed its start")
        hostVersionRetention.pruneAfterCompletedStart(
            chosenHostJar,
            state.copy(
                completedStartVersions = state.completedStartVersions + chosenHostJar.version,
                setAsideVersions = state.setAsideVersions - chosenHostJar.version,
                failedStartCounts = state.failedStartCounts - chosenHostJar.version,
                startedHostProcess = null,
            ),
        )
    }

    /** The host's main threw within its startup time window: a failed start. */
    @Synchronized
    fun recordFailedStart() = recordOutcomeOnce { state ->
        log.write("${chosenHostJar.version} failed within its startup time window")
        failedStartCounter.countFailedStart(state, chosenHostJar.version)
    }

    /**
     * This JVM shuts down within the startup time window, and the host's main did not throw: the
     * user quit, or the host ended itself, as it does to restart. Neither a failed start nor a
     * completed one.
     */
    @Synchronized
    fun recordStartEndedWithoutFailure() = recordOutcomeOnce { state ->
        log.write("${chosenHostJar.version} ended within its startup time window without failing")
        state.copy(startedHostProcess = null)
    }

    private fun recordOutcomeOnce(stateWithOutcome: (LauncherState) -> LauncherState) {
        if (isOutcomeRecorded) return
        isOutcomeRecorded = true
        val launchLock = heldLaunchLock ?: lockFiles.lock(hostDirectory.launchLockFile)
        heldLaunchLock = null
        try {
            hostDirectory.writeLauncherState(stateWithOutcome(hostDirectory.readLauncherState()))
        } finally {
            launchLock.close()
        }
    }
}
