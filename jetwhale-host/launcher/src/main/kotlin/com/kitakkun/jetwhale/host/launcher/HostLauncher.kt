package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostJarCheck
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataReader
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataResult
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
 * @param bundled Null when the launcher runs outside a package, which has no bundled host.
 * @param waitForProcessExit Waits, within reason, for the process with that ID to end.
 */
class HostLauncher(
    private val versions: HostVersionsDirectory,
    private val bundled: BundledHost?,
    private val capabilities: LauncherCapabilities,
    private val metadataReader: HostReleaseMetadataReader,
    private val locks: LockFiles,
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
    fun launch(afterPid: Long?, retryVersion: String?): LaunchOutcome {
        val launchLock = LaunchLock(locks, versions.launchLock)
        try {
            if (afterPid != null) {
                log.write("Waiting for process $afterPid to end")
                waitForProcessExit(afterPid)
            }
            if (retryVersion != null) clearSetAside(retryVersion)
            val instanceProbe = locks.tryLock(versions.instanceLock)
            if (instanceProbe == null) {
                if (runningHost.requestActivation()) {
                    log.write("A host is already running; asked it to bring its window forward")
                    return LaunchOutcome.ActivatedRunningHost
                }
                log.write("A host holds instance.lock but did not answer the request to bring its window forward")
                return LaunchOutcome.RunningHostUnreachable
            }
            instanceProbe.close()
            return chooseAndStart(launchLock)
        } finally {
            launchLock.release()
        }
    }

    private fun clearSetAside(version: String) {
        val state = versions.readLauncherState()
        if (version !in state.setAside) return
        log.write("Trying $version again")
        versions.writeLauncherState(state.copy(setAside = state.setAside - version))
    }

    private fun chooseAndStart(launchLock: LaunchLock): LaunchOutcome {
        val progress = LaunchProgress()
        for (candidate in candidates()) {
            val start = when (candidate) {
                is Candidate.Downloaded -> verify(candidate.installed, versions.readLauncherState()) ?: continue
                is Candidate.Bundled -> candidate.host.start
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
        val first = attempt(start, progress.setAsideThisLaunch, launchLock)
        val last = if (first.outcome is StartupOutcome.Failed && !hasCompletedBefore(start, launchLock)) {
            log.write("${start.name} has never completed a start here; starting it once more")
            attempt(start, progress.setAsideThisLaunch, launchLock)
        } else {
            first
        }
        return when (val outcome = last.outcome) {
            is StartupOutcome.Completed -> {
                log.write("${start.name} completed its start")
                launchLock.ensureHeld()
                recordCompletedStart(start, last.process)
                LaunchOutcome.Started(start.name, last.process)
            }

            is StartupOutcome.Neither -> {
                log.write("${start.name} ended within the startup window without failing")
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
        return start.name in versions.readLauncherState().completedStarts
    }

    /**
     * Records the start on what is on disk now, which another launcher may have changed while
     * `launch.lock` was released, and clears a set-aside mark it may have put on this version
     * meanwhile. Pruning keeps what the running version needs, so it is skipped once that host has
     * ended: another launch may run another version by now.
     */
    private fun recordCompletedStart(start: HostStart, process: HostProcess) {
        val state = versions.readLauncherState()
        val recorded = state.copy(completedStarts = state.completedStarts + start.name, setAside = state.setAside - start.name)
        versions.writeLauncherState(if (process.exitStatus() == null) pruneAfterStart(start, recorded) else recorded)
    }

    private fun afterFailedStart(
        start: HostStart,
        failure: StartupOutcome.Failed,
        progress: LaunchProgress,
        launchLock: LaunchLock,
    ): LaunchOutcome? {
        when {
            hasCompletedBefore(start, launchLock) -> {
                log.write("${start.name} has completed a start before, so it is not set aside; its crash recovery takes over")
                return LaunchOutcome.Crashed(start.name, failure.exitStatus)
            }

            start.isBundled -> log.write("The bundled ${start.name} failed its first starts")

            else -> {
                log.write("Setting ${start.name} aside")
                val state = versions.readLauncherState()
                versions.writeLauncherState(state.copy(setAside = state.setAside + start.name))
                progress.setAsideThisLaunch = progress.setAsideThisLaunch ?: start.name
            }
        }
        return null
    }

    private fun candidates(): List<Candidate> {
        val floor = bundled?.start?.version
        val downloaded = versions.installedVersions()
            .filter { floor == null || it.version > floor }
            .map(Candidate::Downloaded)
        return downloaded + listOfNotNull(bundled?.let(Candidate::Bundled))
    }

    private fun verify(installed: InstalledHostVersion, state: LauncherState): HostStart? {
        val name = installed.name
        if (name in state.setAside) {
            log.write("Skipping $name: it is set aside")
            return null
        }
        val metadataBytes = readOrNull(installed.metadataFile)
            ?: return discard(installed, "it has no ${InstalledHostVersion.METADATA_FILE_NAME}")
        val metadata = when (val read = metadataReader.read(metadataBytes, readOrNull(installed.signatureFile))) {
            is HostReleaseMetadataResult.Read -> read.metadata

            is HostReleaseMetadataResult.NewerFormat -> {
                log.write("Skipping $name: its metadata has format ${read.format}, which this launcher cannot read")
                return null
            }

            HostReleaseMetadataResult.Untrusted -> return discard(installed, "its metadata signature does not verify")

            is HostReleaseMetadataResult.Malformed -> return discard(installed, "its metadata is malformed (${read.reason})")
        }
        if (metadata.version != name) return discard(installed, "its metadata is for ${metadata.version}")
        val refusal = metadata.refusalOn(capabilities)
        if (refusal != null) {
            log.write("Skipping $name: $refusal")
            return null
        }
        val jar = installed.jar(capabilities.platformKey)
        if (!versions.contains(jar)) return discard(installed, "its jar resolves outside ${versions.root}")
        val check = try {
            metadata.platforms.getValue(capabilities.platformKey).check(jar)
        } catch (e: IOException) {
            return discard(installed, "its jar cannot be read (${e.message})")
        }
        if (check != HostJarCheck.Matches) return discard(installed, "its jar does not match its metadata ($check)")
        return HostStart(name = name, version = installed.version, metadata = metadata, jar = jar, isBundled = false)
    }

    private fun discard(installed: InstalledHostVersion, reason: String): HostStart? {
        log.write("Deleting ${installed.name}: $reason")
        delete(installed)
        return null
    }

    private fun delete(installed: InstalledHostVersion) {
        if (!versions.delete(installed)) log.write("Could not delete all of ${installed.name}; a later start tries again")
    }

    private fun attempt(start: HostStart, setAside: String?, launchLock: LaunchLock): HostAttempt {
        launchLock.ensureHeld()
        if (!isInstanceHeldElsewhere()) versions.deleteInstanceRecord()
        log.write("Starting ${start.name}${if (start.isBundled) " (bundled)" else ""}")
        val process = hostProcesses.start(start, setAside)
        var published = false
        val exitStatus = startupWindow.watch(process) {
            if (!published && runningHost.isPublishedBy(process.pid)) {
                published = true
                launchLock.release()
            }
        }
        val outcome = when {
            exitStatus == null -> StartupOutcome.Completed

            published || runningHost.isPublishedBy(process.pid) ->
                if (exitStatus == 0) StartupOutcome.Neither else StartupOutcome.Failed(exitStatus)

            isInstanceHeldElsewhere() -> StartupOutcome.Neither

            else -> StartupOutcome.Failed(exitStatus)
        }
        if (outcome is StartupOutcome.Failed) {
            log.write("${start.name} exited with status ${outcome.exitStatus} within the startup window")
        }
        return HostAttempt(process, outcome)
    }

    private fun isInstanceHeldElsewhere(): Boolean {
        val probe = locks.tryLock(versions.instanceLock) ?: return true
        probe.close()
        return false
    }

    /**
     * Keeps the running version and the newest version newer than it, which is set aside or finished
     * downloading during this start, and deletes every other downloaded version.
     */
    private fun pruneAfterStart(running: HostStart, state: LauncherState): LauncherState {
        val installed = versions.installedVersions()
        val newestNewer = installed.firstOrNull { it.version > running.version }
        installed
            .filterNot { it == newestNewer || (!running.isBundled && it.name == running.name) }
            .forEach {
                log.write("Deleting ${it.name}: ${running.name} completed a start")
                delete(it)
            }
        val kept = versions.installedVersions().map(InstalledHostVersion::name).toSet() + listOfNotNull(bundled?.start?.name)
        return LauncherState(
            completedStarts = state.completedStarts.intersect(kept),
            setAside = state.setAside.intersect(kept),
        )
    }

    private fun readOrNull(file: Path): ByteArray? = try {
        Files.readAllBytes(file)
    } catch (_: IOException) {
        null
    }

    private sealed interface Candidate {
        data class Downloaded(val installed: InstalledHostVersion) : Candidate

        data class Bundled(val host: BundledHost) : Candidate
    }

    private class HostAttempt(val process: HostProcess, val outcome: StartupOutcome)

    private class LaunchProgress {
        /** The first version this launch set aside, which the host it starts next names. */
        var setAsideThisLaunch: String? = null
    }
}

sealed interface LaunchOutcome {
    /** A version was still running at the end of its startup window. */
    data class Started(val version: String, val process: HostProcess) : LaunchOutcome

    /** The started host ended within its startup window without failing ([StartupOutcome.Neither]). */
    data object Neither : LaunchOutcome

    data object ActivatedRunningHost : LaunchOutcome

    data object RunningHostUnreachable : LaunchOutcome

    /** A version that had completed a start before failed within its startup window. */
    data class Crashed(val version: String, val exitStatus: Int) : LaunchOutcome

    data object NothingLeft : LaunchOutcome
}
