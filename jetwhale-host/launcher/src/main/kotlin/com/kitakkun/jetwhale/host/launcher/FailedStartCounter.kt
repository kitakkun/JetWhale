package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.LauncherState

/**
 * Counts failed starts in `launcher-state.json`. A version that has completed a start before is not
 * counted, so it is never set aside: once it has run on this machine, a plugin is the likelier cause
 * of a failure than the version.
 */
class FailedStartCounter(private val log: LauncherLog) {
    /** [launcherState] with a failed start of [version] counted, and its started host process cleared. */
    fun countFailedStart(launcherState: LauncherState, version: HostVersion): LauncherState {
        if (version in launcherState.completedStartVersions) {
            log.write("$version has completed a start before, so this failure is not counted")
            return launcherState.copy(startedHostProcess = null)
        }
        val count = (launcherState.failedStartCounts[version] ?: 0) + 1
        log.write("$version has failed $count start(s) in a row")
        return launcherState.copy(failedStartCounts = launcherState.failedStartCounts + (version to count), startedHostProcess = null)
    }
}
