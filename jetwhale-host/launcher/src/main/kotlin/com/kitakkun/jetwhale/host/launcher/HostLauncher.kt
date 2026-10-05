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
import com.kitakkun.jetwhale.host.release.StartingHost
import com.kitakkun.jetwhale.host.release.check
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** How many starts in a row a version that has never completed one may fail before it is set aside. */
private const val FAILED_STARTS_TO_SET_ASIDE = 2

/**
 * Chooses the host version this process runs, and records how its start goes.
 *
 * Under `launch.lock`, it first judges a start that an earlier launch wrote down and never judged: when
 * that process has ended, it crashed or was killed within its startup window, which is a failed start.
 * Then it hands the launch to a host that is already running. Otherwise it goes through the downloaded
 * versions newer than the bundled one, newest first, then the bundled one, and picks the first that is
 * not set aside, not refused and verifies. A downloaded version that has never completed a start and
 * has failed its last two is set aside on the way.
 *
 * @param bundledHost Null when the launcher runs outside a package, which has no bundled host.
 * @param processes This process, which runs the chosen host, and those of earlier launches.
 */
class HostLauncher(
    private val hostVersionsDirectory: HostVersionsDirectory,
    private val bundledHost: BundledHost?,
    private val capabilities: LauncherCapabilities,
    private val metadataReader: HostReleaseMetadataReader,
    private val lockFiles: LockFiles,
    private val runningHost: RunningHostChannel,
    private val processes: ProcessTable,
    private val log: LauncherLog,
) {
    /**
     * Returns [LaunchOutcome.Starting] still holding `launch.lock`, which its [HostStartup] lets go.
     *
     * @param afterPid The host that restarts into an update, which has to end before a version is
     * chosen. It is waited for before `launch.lock` is taken, because that host takes the lock on its
     * way out when it restarts within its startup window.
     * @param retryVersion A set-aside version the user asked to try again.
     */
    fun launch(afterPid: Long?, retryVersion: HostVersion?): LaunchOutcome {
        if (afterPid != null) {
            log.write("Waiting for process $afterPid to end")
            processes.awaitExit(afterPid)
        }
        val launchLock = LaunchLock(lockFiles, hostVersionsDirectory.launchLockFile)
        var handedOver = false
        try {
            judgeEndedStart()
            if (retryVersion != null) clearSetAside(retryVersion)
            if (isInstanceHeldElsewhere()) return handToRunningHost()
            val outcome = chooseAndStart(launchLock)
            handedOver = outcome is LaunchOutcome.Starting
            return outcome
        } finally {
            if (!handedOver) launchLock.release()
        }
    }

    /**
     * A start still written down while its process no longer runs ended without this launcher's
     * shutdown hook or failure handling: a crash, an hs_err, or a kill.
     */
    private fun judgeEndedStart() {
        val state = hostVersionsDirectory.readLauncherState()
        val startingHost = state.startingHost ?: return
        if (processes.isRunning(startingHost.pid, startingHost.processStartMillis)) return
        log.write("${startingHost.version} ended within its startup window without a record of how: a crash or a kill")
        hostVersionsDirectory.writeLauncherState(failedStart(state, startingHost.version))
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
        if (version !in state.setAsideVersions && version !in state.failedStartCounts) return
        log.write("Trying $version again")
        hostVersionsDirectory.writeLauncherState(
            state.copy(setAsideVersions = state.setAsideVersions - version, failedStartCounts = state.failedStartCounts - version),
        )
    }

    private fun chooseAndStart(launchLock: LaunchLock): LaunchOutcome {
        var firstSetAsideVersion: HostVersion? = null
        for (candidate in candidates()) {
            val start = when (candidate) {
                is Candidate.Downloaded -> {
                    val version = candidate.installedVersion.version
                    if (setAsideAfterFailedStarts(version)) firstSetAsideVersion = firstSetAsideVersion ?: version
                    verify(candidate.installedVersion, hostVersionsDirectory.readLauncherState()) ?: continue
                }

                is Candidate.Bundled -> candidate.bundledHost.start
            }
            return startInThisProcess(start, firstSetAsideVersion, launchLock)
        }
        log.write("No host version is left to start")
        return LaunchOutcome.NothingLeft
    }

    /**
     * Sets [version] aside when it has failed its last starts; returns whether it did. Only a version
     * that has never completed a start has failures counted.
     */
    private fun setAsideAfterFailedStarts(version: HostVersion): Boolean {
        val state = hostVersionsDirectory.readLauncherState()
        if (version in state.setAsideVersions || (state.failedStartCounts[version] ?: 0) < FAILED_STARTS_TO_SET_ASIDE) return false
        log.write("Setting $version aside: it failed its last $FAILED_STARTS_TO_SET_ASIDE starts")
        hostVersionsDirectory.writeLauncherState(state.copy(setAsideVersions = state.setAsideVersions + version))
        return true
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
     * Writes down that this process starts [start], so that a later launch counts a crash within its
     * startup window. An instance record a host left behind goes first: that host could have had this
     * process's ID, and its record would pass for this host's.
     */
    private fun startInThisProcess(start: HostStart, setAsideVersion: HostVersion?, launchLock: LaunchLock): LaunchOutcome.Starting {
        hostVersionsDirectory.deleteInstanceRecord()
        val state = hostVersionsDirectory.readLauncherState()
        hostVersionsDirectory.writeLauncherState(
            state.copy(startingHost = StartingHost(start.version, processes.currentPid, processes.currentStartMillis)),
        )
        log.write("Starting ${start.version}${if (start.isBundled) " (bundled)" else ""}")
        return LaunchOutcome.Starting(start, setAsideVersion, HostStartup(start, launchLock))
    }

    private fun isInstanceHeldElsewhere(): Boolean {
        val probe = lockFiles.tryLock(hostVersionsDirectory.instanceLockFile) ?: return true
        probe.close()
        return false
    }

    /** Counts a failed start of [version], unless it has completed one before: its own crash recovery handles that. */
    private fun failedStart(state: LauncherState, version: HostVersion): LauncherState {
        if (version in state.completedStartVersions) {
            log.write("$version has completed a start before, so this failure is not counted; its crash recovery takes over")
            return state.copy(startingHost = null)
        }
        val count = (state.failedStartCounts[version] ?: 0) + 1
        log.write("$version has failed $count start(s) in a row")
        return state.copy(failedStartCounts = state.failedStartCounts + (version to count), startingHost = null)
    }

    private fun readOrNull(file: Path): ByteArray? = try {
        Files.readAllBytes(file)
    } catch (_: IOException) {
        null
    }

    /**
     * How the start of [start] in this process ends. The first of three ends counts, and is recorded
     * once, under `launch.lock`: the end of the startup window, a failure of the host's main, or a
     * shutdown of this JVM. Until then it holds the `launch.lock` the launch took, or lets it go once
     * the host has published its record.
     */
    inner class HostStartup internal constructor(private val start: HostStart, private val launchLock: LaunchLock) {
        private var judged = false

        /** The host published its record, where a later launch finds it, so `launch.lock` can go. */
        @Synchronized
        fun hostPublished() {
            if (!judged) launchLock.release()
        }

        /** The host still runs at the end of its startup window: it completed its start. */
        @Synchronized
        fun windowEnded() = judge { state ->
            log.write("${start.version} completed its start")
            pruneAfterStart(
                start,
                state.copy(
                    completedStartVersions = state.completedStartVersions + start.version,
                    setAsideVersions = state.setAsideVersions - start.version,
                    failedStartCounts = state.failedStartCounts - start.version,
                    startingHost = null,
                ),
            )
        }

        /** The host's main threw within the window: a failed start. */
        @Synchronized
        fun hostFailed() = judge { state ->
            log.write("${start.version} failed within its startup window")
            failedStart(state, start.version)
        }

        /**
         * This JVM shuts down within the window, and the host's main did not throw: the user quit, or
         * the host ended itself, as it does to restart. Neither a failed start nor a completed one.
         */
        @Synchronized
        fun shutDownWithoutFailure() = judge { state ->
            log.write("${start.version} ended within its startup window without failing")
            state.copy(startingHost = null)
        }

        private fun judge(record: (LauncherState) -> LauncherState) {
            if (judged) return
            judged = true
            launchLock.ensureHeld()
            try {
                hostVersionsDirectory.writeLauncherState(record(hostVersionsDirectory.readLauncherState()))
            } finally {
                launchLock.release()
            }
        }
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
        return launcherState.copy(
            completedStartVersions = launcherState.completedStartVersions.intersect(keptVersions),
            setAsideVersions = launcherState.setAsideVersions.intersect(keptVersions),
            failedStartCounts = launcherState.failedStartCounts.filterKeys(keptVersions::contains),
        )
    }

    private sealed interface Candidate {
        data class Downloaded(val installedVersion: InstalledHostVersion) : Candidate

        data class Bundled(val bundledHost: BundledHost) : Candidate
    }
}

sealed interface LaunchOutcome {
    /**
     * This process runs [start]. [startup] records how that start goes.
     *
     * @property setAsideVersion A version this launch set aside, which the host tells the user about.
     */
    class Starting(
        val start: HostStart,
        val setAsideVersion: HostVersion?,
        val startup: HostLauncher.HostStartup,
    ) : LaunchOutcome

    data object ActivatedRunningHost : LaunchOutcome

    data object RunningHostUnreachable : LaunchOutcome

    data object NothingLeft : LaunchOutcome
}
