package com.kitakkun.jetwhale.host.release

import kotlinx.serialization.Serializable

/**
 * What `launcher-state.json` holds. Only the launcher writes it; the host reads it.
 *
 * @property completedStartVersions The versions that were still running at the end of a startup time
 * window on this machine. Such a version is never set aside again.
 * @property setAsideVersions The versions that failed their first starts, until the user tries them
 * again. An entry for a version whose directory is gone means nothing.
 * @property failedStartCounts How many starts in a row each version that has not completed one has
 * failed.
 * @property startedHostProcess The host process whose start has no recorded outcome yet: it is in its
 * startup time window, or it ended without the outcome being recorded.
 */
@Serializable
data class LauncherState(
    val completedStartVersions: Set<HostVersion>,
    val setAsideVersions: Set<HostVersion>,
    val failedStartCounts: Map<HostVersion, Int>,
    val startedHostProcess: StartedHostProcess?,
) {
    companion object {
        val EMPTY = LauncherState(
            completedStartVersions = emptySet(),
            setAsideVersions = emptySet(),
            failedStartCounts = emptyMap(),
            startedHostProcess = null,
        )
    }
}

/**
 * The process [pid] the launcher started [version] in, written down before the host's main is called.
 * Its presence in `launcher-state.json` marks a start whose outcome is not recorded yet.
 *
 * @property processStartMillis When that process started, in milliseconds since the epoch, which
 * tells it from a later process that got the same ID; null when the OS did not say.
 */
@Serializable
data class StartedHostProcess(
    val version: HostVersion,
    val pid: Long,
    val processStartMillis: Long?,
)
