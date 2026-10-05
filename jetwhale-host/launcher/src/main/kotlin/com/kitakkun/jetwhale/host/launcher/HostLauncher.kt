package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostJarCheck
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataReader
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataResult
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.HostVersionsDirectory
import com.kitakkun.jetwhale.host.release.InstalledHostVersion
import com.kitakkun.jetwhale.host.release.LauncherCapabilities
import com.kitakkun.jetwhale.host.release.LauncherState
import com.kitakkun.jetwhale.host.release.LockFiles
import com.kitakkun.jetwhale.host.release.check
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Chooses a host version and starts it.
 *
 * Under `launch.lock`, it first hands the launch to a host that is already running. Otherwise it goes
 * through the downloaded versions newer than the bundled one, newest first, then the bundled one, and
 * starts the first that is not set aside, not refused and verifies. A version that has never
 * completed a start gets a second try when it fails; when that fails too, it is set aside and the
 * launch goes on to the next version.
 *
 * @param bundledHost Null when the launcher runs outside a package, which has no bundled host.
 * @param waitForProcessExit Waits, within reason, for the process with that ID to end.
 */
class HostLauncher(
    private val hostVersionsDirectory: HostVersionsDirectory,
    private val bundledHost: BundledHost?,
    private val capabilities: LauncherCapabilities,
    private val metadataReader: HostReleaseMetadataReader,
    private val lockFiles: LockFiles,
    private val runningHost: RunningHostChannel,
    private val hostProcesses: HostProcesses,
    private val startupWindow: StartupWindow,
    private val log: LauncherLog,
    private val waitForProcessExit: (pid: Long) -> Unit,
) {
    /**
     * @param afterPid The host that restarts into an update, which has to end before a version is
     * chosen.
     * @param retryVersion A set-aside version the user asked to try again.
     */
    fun launch(afterPid: Long?, retryVersion: HostVersion?): LaunchOutcome {
        val launchLock = LaunchLock(lockFiles, hostVersionsDirectory.launchLockFile)
        try {
            if (afterPid != null) {
                log.write("Waiting for process $afterPid to end")
                waitForProcessExit(afterPid)
            }
            if (retryVersion != null) clearSetAside(retryVersion)
            if (isInstanceHeldElsewhere()) return handToRunningHost()
            return chooseAndStart(launchLock)
        } finally {
            launchLock.release()
        }
    }

    private fun handToRunningHost(): LaunchOutcome {
        if (runningHost.requestActivation()) {
            log.write("A host is already running; asked it to bring its window forward")
            return LaunchOutcome.ActivatedRunningHost
        }
        log.write("A host holds instance.lock but did not answer the request to bring its window forward")
        return LaunchOutcome.RunningHostUnreachable
    }

    private fun clearSetAside(version: HostVersion) {
        val state = hostVersionsDirectory.readLauncherState()
        if (version !in state.setAsideVersions) return
        log.write("Trying $version again")
        hostVersionsDirectory.writeLauncherState(state.copy(setAsideVersions = state.setAsideVersions - version))
    }

    private fun chooseAndStart(launchLock: LaunchLock): LaunchOutcome {
        val progress = LaunchProgress()
        for (candidate in candidates()) {
            val start = when (candidate) {
                is Candidate.Downloaded -> verify(candidate.installedVersion, hostVersionsDirectory.readLauncherState()) ?: continue
                is Candidate.Bundled -> candidate.bundledHost.start
            }
            return tryCandidate(start, progress, launchLock) ?: continue
        }
        log.write("No host version is left to start")
        return LaunchOutcome.NothingLeft
    }

    /**
     * Starts [start], once more when it fails and has never completed a start here. Returns how the
     * launch ends, or null when it goes on to the next candidate.
     */
    private fun tryCandidate(start: HostStart, progress: LaunchProgress, launchLock: LaunchLock): LaunchOutcome? {
        val first = attempt(start, progress.firstSetAsideVersion, launchLock) ?: return handToRunningHost()
        val last = if (first.outcome is StartupOutcome.Failed && !hasCompletedBefore(start, launchLock)) {
            log.write("${start.version} has never completed a start here; starting it once more")
            attempt(start, progress.firstSetAsideVersion, launchLock) ?: return handToRunningHost()
        } else {
            first
        }
        return when (val outcome = last.outcome) {
            is StartupOutcome.Completed -> {
                log.write("${start.version} completed its start")
                launchLock.ensureHeld()
                recordCompletedStart(start, last.process)
                LaunchOutcome.Started(start.version, last.process)
            }

            is StartupOutcome.Neither -> {
                log.write("${start.version} ended within the startup window without failing")
                LaunchOutcome.Neither
            }

            is StartupOutcome.Failed -> afterFailedStart(start, outcome, progress, launchLock)
        }
    }

    /**
     * Reads the record under `launch.lock`: while it was released, another launcher may have
     * recorded a start of this version.
     */
    private fun hasCompletedBefore(start: HostStart, launchLock: LaunchLock): Boolean {
        launchLock.ensureHeld()
        return start.version in hostVersionsDirectory.readLauncherState().completedStartVersions
    }

    /**
     * Records the start on what is on disk now, which another launcher may have changed while
     * `launch.lock` was released, and clears a set-aside mark it may have put on this version
     * meanwhile. Pruning keeps what the running version needs, so it is skipped once that host has
     * ended: another launch may run another version by now.
     */
    private fun recordCompletedStart(start: HostStart, process: HostProcess) {
        val state = hostVersionsDirectory.readLauncherState()
        val recorded = state.copy(completedStartVersions = state.completedStartVersions + start.version, setAsideVersions = state.setAsideVersions - start.version)
        hostVersionsDirectory.writeLauncherState(if (process.exitStatus() == null) pruneAfterStart(start, recorded) else recorded)
    }

    private fun afterFailedStart(
        start: HostStart,
        failure: StartupOutcome.Failed,
        progress: LaunchProgress,
        launchLock: LaunchLock,
    ): LaunchOutcome? {
        when {
            hasCompletedBefore(start, launchLock) -> {
                log.write("${start.version} has completed a start before, so it is not set aside; its crash recovery takes over")
                return LaunchOutcome.Crashed(start.version, failure.exitStatus)
            }

            start.isBundled -> log.write("The bundled ${start.version} failed its first starts")

            else -> {
                log.write("Setting ${start.version} aside")
                val state = hostVersionsDirectory.readLauncherState()
                hostVersionsDirectory.writeLauncherState(state.copy(setAsideVersions = state.setAsideVersions + start.version))
                progress.firstSetAsideVersion = progress.firstSetAsideVersion ?: start.version
            }
        }
        return null
    }

    private fun candidates(): List<Candidate> {
        val floor = bundledHost?.start?.version
        val downloaded = hostVersionsDirectory.installedVersions()
            .filter { floor == null || it.version > floor }
            .map(Candidate::Downloaded)
        return downloaded + listOfNotNull(bundledHost?.let(Candidate::Bundled))
    }

    private fun verify(installedVersion: InstalledHostVersion, launcherState: LauncherState): HostStart? {
        val version = installedVersion.version
        if (version in launcherState.setAsideVersions) {
            log.write("Skipping $version: it is set aside")
            return null
        }
        val metadataBytes = readOrNull(installedVersion.metadataFile)
            ?: return discard(installedVersion, "it has no ${InstalledHostVersion.METADATA_FILE_NAME}")
        val metadata = when (val read = metadataReader.read(metadataBytes, readOrNull(installedVersion.signatureFile))) {
            is HostReleaseMetadataResult.Read -> read.metadata

            is HostReleaseMetadataResult.NewerFormat -> {
                log.write("Skipping $version: its metadata has format ${read.format}, which this launcher cannot read")
                return null
            }

            HostReleaseMetadataResult.Untrusted -> return discard(installedVersion, "its metadata signature does not verify")

            is HostReleaseMetadataResult.Malformed -> return discard(installedVersion, "its metadata is malformed (${read.reason})")
        }
        if (metadata.version.name != version.name) return discard(installedVersion, "its metadata is for ${metadata.version}")
        val refusal = metadata.refusalOn(capabilities)
        if (refusal != null) {
            log.write("Skipping $version: $refusal")
            return null
        }
        val jar = installedVersion.jarFile(capabilities.platformKey)
        if (!hostVersionsDirectory.contains(jar)) return discard(installedVersion, "its jar resolves outside ${hostVersionsDirectory.root}")
        val check = try {
            metadata.platforms.getValue(capabilities.platformKey).check(jar)
        } catch (e: IOException) {
            return discard(installedVersion, "its jar cannot be read (${e.message})")
        }
        if (check != HostJarCheck.Matches) return discard(installedVersion, "its jar does not match its metadata ($check)")
        return HostStart(version = version, metadata = metadata, jar = jar, isBundled = false)
    }

    private fun discard(installedVersion: InstalledHostVersion, reason: String): HostStart? {
        log.write("Deleting ${installedVersion.version}: $reason")
        delete(installedVersion)
        return null
    }

    private fun delete(installedVersion: InstalledHostVersion) {
        if (!hostVersionsDirectory.delete(installedVersion)) log.write("Could not delete all of ${installedVersion.version}; a later start tries again")
    }

    /**
     * Starts [start] and watches its startup window, or returns null without starting it when a host
     * holds `instance.lock`: while `launch.lock` was released, another launcher may have started one.
     */
    private fun attempt(start: HostStart, setAsideVersion: HostVersion?, launchLock: LaunchLock): HostAttempt? {
        launchLock.ensureHeld()
        if (isInstanceHeldElsewhere()) return null
        hostVersionsDirectory.deleteInstanceRecord()
        log.write("Starting ${start.version}${if (start.isBundled) " (bundled)" else ""}")
        val process = hostProcesses.start(start, setAsideVersion)
        var published = false
        val exitStatus = startupWindow.watch(process) {
            if (!published && runningHost.isPublishedBy(process.pid)) {
                published = true
                launchLock.release()
            }
        }
        val outcome = when (exitStatus) {
            null -> StartupOutcome.Completed
            0 -> StartupOutcome.Neither
            else -> StartupOutcome.Failed(exitStatus)
        }
        if (outcome is StartupOutcome.Failed) {
            log.write("${start.version} exited with status ${outcome.exitStatus} within the startup window")
        }
        return HostAttempt(process, outcome)
    }

    private fun isInstanceHeldElsewhere(): Boolean {
        val probe = lockFiles.tryLock(hostVersionsDirectory.instanceLockFile) ?: return true
        probe.close()
        return false
    }

    /**
     * Keeps the running version and the newest version newer than it, which is set aside or finished
     * downloading during this start, and deletes every other downloaded version.
     */
    private fun pruneAfterStart(running: HostStart, launcherState: LauncherState): LauncherState {
        val installedVersions = hostVersionsDirectory.installedVersions()
        val newestNewer = installedVersions.firstOrNull { it.version > running.version }
        installedVersions
            .filterNot { it == newestNewer || (!running.isBundled && it.version == running.version) }
            .forEach {
                log.write("Deleting ${it.version}: ${running.version} completed a start")
                delete(it)
            }
        val keptVersions = hostVersionsDirectory.installedVersions().map(InstalledHostVersion::version).toSet() + listOfNotNull(bundledHost?.start?.version)
        return LauncherState(
            completedStartVersions = launcherState.completedStartVersions.intersect(keptVersions),
            setAsideVersions = launcherState.setAsideVersions.intersect(keptVersions),
        )
    }

    private fun readOrNull(file: Path): ByteArray? = try {
        Files.readAllBytes(file)
    } catch (_: IOException) {
        null
    }

    private sealed interface Candidate {
        data class Downloaded(val installedVersion: InstalledHostVersion) : Candidate

        data class Bundled(val bundledHost: BundledHost) : Candidate
    }

    private class HostAttempt(val process: HostProcess, val outcome: StartupOutcome)

    private class LaunchProgress {
        /** The first version this launch set aside, which the host it starts next names. */
        var firstSetAsideVersion: HostVersion? = null
    }
}

sealed interface LaunchOutcome {
    /** A version was still running at the end of its startup window. */
    data class Started(val version: HostVersion, val process: HostProcess) : LaunchOutcome

    /** The started host ended within its startup window without failing ([StartupOutcome.Neither]). */
    data object Neither : LaunchOutcome

    data object ActivatedRunningHost : LaunchOutcome

    data object RunningHostUnreachable : LaunchOutcome

    /** A version that had completed a start before failed within its startup window. */
    data class Crashed(val version: HostVersion, val exitStatus: Int) : LaunchOutcome

    data object NothingLeft : LaunchOutcome
}
