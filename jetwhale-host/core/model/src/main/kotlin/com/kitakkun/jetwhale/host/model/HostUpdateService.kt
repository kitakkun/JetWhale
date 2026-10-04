package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.StateFlow

/**
 * Finds newer host releases, downloads and verifies the one the user asks for, and installs it for
 * the launcher's next start. A host the launcher did not start only links to the releases.
 */
interface HostUpdateService {
    val stateFlow: StateFlow<HostUpdateState>

    /** Looks up the newest release, unless a download is under way. */
    suspend fun check()

    /** Starts downloading the release [check] found; the state reports its progress. */
    fun download()

    fun cancelDownload()

    /**
     * Starts the launcher to choose a version once this process has ended. When it returns true, the
     * caller shuts the host down; false means no launcher can be started, and the host stays.
     *
     * @param retryVersion A set-aside version for the launcher to try again.
     */
    fun startLauncherAfterExit(retryVersion: String?): Boolean
}
