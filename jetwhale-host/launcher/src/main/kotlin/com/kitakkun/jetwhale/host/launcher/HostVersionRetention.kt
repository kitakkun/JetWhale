package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostDirectory
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.HostVersionDirectory
import com.kitakkun.jetwhale.host.release.LauncherState

/**
 * Which downloaded versions stay once a version has completed a start: that version and the newest
 * version newer than it, which is set aside or finished downloading during that start. Every other
 * one goes.
 *
 * @param bundledHostVersion The version installed with the package, which is never deleted; null when
 * the launcher runs outside a package.
 */
class HostVersionRetention(
    private val hostDirectory: HostDirectory,
    private val bundledHostVersion: HostVersion?,
    private val log: LauncherLog,
) {
    /**
     * Deletes the downloaded versions that do not stay after [chosenHostJar] completed its start, and
     * returns [launcherState] without the entries of versions that are gone.
     */
    fun pruneAfterCompletedStart(chosenHostJar: ChosenHostJar, launcherState: LauncherState): LauncherState {
        val hostVersionDirectories = hostDirectory.hostVersionDirectories()
        val newestNewer = hostVersionDirectories.firstOrNull { it.version > chosenHostJar.version }
        hostVersionDirectories
            .filterNot { it == newestNewer || (!chosenHostJar.isBundled && it.version == chosenHostJar.version) }
            .forEach {
                log.write("Deleting ${it.version}: ${chosenHostJar.version} completed a start")
                if (!hostDirectory.delete(it)) log.write("Could not delete all of ${it.version}; a later start tries again")
            }
        val keptVersions = hostDirectory.hostVersionDirectories().map(HostVersionDirectory::version).toSet() + listOfNotNull(bundledHostVersion)
        return launcherState.copy(
            completedStartVersions = launcherState.completedStartVersions.intersect(keptVersions),
            setAsideVersions = launcherState.setAsideVersions.intersect(keptVersions),
            failedStartCounts = launcherState.failedStartCounts.filterKeys(keptVersions::contains),
        )
    }
}
