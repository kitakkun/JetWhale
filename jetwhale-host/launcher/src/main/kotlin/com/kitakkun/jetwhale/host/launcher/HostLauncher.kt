package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostDirectory
import com.kitakkun.jetwhale.host.release.HostJarCheck
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataReader
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataResult
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.HostVersionDirectory
import com.kitakkun.jetwhale.host.release.LauncherCapabilities
import com.kitakkun.jetwhale.host.release.LauncherState
import com.kitakkun.jetwhale.host.release.LockFiles
import com.kitakkun.jetwhale.host.release.StartInProgress
import com.kitakkun.jetwhale.host.release.check
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration

/** How many starts in a row a version that has never completed one may fail before it is set aside. */
private const val FAILED_STARTS_TO_SET_ASIDE = 2

/**
 * Chooses the host version this process runs, and records how its start goes.
 *
 * Under `launch.lock`, it first judges a start that an earlier launch wrote down and never judged: when
 * that process has ended, it crashed or was killed within its startup time window, which is a failed
 * start. Then it hands the launch to a host that is already running. Otherwise it goes through the
 * downloaded versions newer than the bundled one, newest first, then the bundled one, and picks the
 * first that is not set aside, not refused and verifies. A downloaded version that has never completed
 * a start and has failed its last two is set aside on the way.
 *
 * @param bundledHostVersion Null when the launcher runs outside a package, which has no bundled host.
 * @param processTable This process, which runs the chosen host, and those of earlier launches.
 */
class HostLauncher(
    private val hostDirectory: HostDirectory,
    private val bundledHostVersion: ChosenHostVersion?,
    private val capabilities: LauncherCapabilities,
    private val metadataReader: HostReleaseMetadataReader,
    private val lockFiles: LockFiles,
    private val runningHostChannel: RunningHostChannel,
    private val processTable: ProcessTable,
    private val log: LauncherLog,
    private val sleep: (Duration) -> Unit,
) {
    /**
     * Returns [LaunchOutcome.Starting] still holding `launch.lock`, which its [HostStart] lets go.
     *
     * @param afterPid The host that restarts into an update, which has to end before a version is
     * chosen. It is waited for before `launch.lock` is taken, because that host takes the lock on its
     * way out when it restarts within its startup time window.
     * @param retryVersion A set-aside version the user asked to try again.
     */
    fun launch(afterPid: Long?, retryVersion: HostVersion?): LaunchOutcome {
        if (afterPid != null) {
            log.write("Waiting for process $afterPid to end")
            processTable.awaitExit(afterPid)
        }
        val launchLock = LaunchLock(lockFiles, hostDirectory.launchLockFile)
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
        val state = hostDirectory.readLauncherState()
        val startInProgress = state.startInProgress ?: return
        if (processTable.isRunning(startInProgress.pid, startInProgress.processStartMillis)) return
        log.write("${startInProgress.version} ended within its startup time window without a record of how: a crash or a kill")
        hostDirectory.writeLauncherState(failedStart(state, startInProgress.version))
    }

    private fun handToRunningHost(): LaunchOutcome {
        if (runningHostChannel.requestBringToFront()) {
            log.write("A host is already running; asked it to bring its window forward")
            return LaunchOutcome.BroughtRunningHostToFront
        }
        log.write("A host holds instance.lock but did not answer the request to bring its window forward")
        return LaunchOutcome.RunningHostUnreachable
    }

    private fun clearSetAside(version: HostVersion) {
        val state = hostDirectory.readLauncherState()
        if (version !in state.setAsideVersions && version !in state.failedStartCounts) return
        log.write("Trying $version again")
        hostDirectory.writeLauncherState(
            state.copy(setAsideVersions = state.setAsideVersions - version, failedStartCounts = state.failedStartCounts - version),
        )
    }

    private fun chooseAndStart(launchLock: LaunchLock): LaunchOutcome {
        var firstSetAsideVersion: HostVersion? = null
        for (candidate in candidates()) {
            val chosenHostVersion = when (candidate) {
                is Candidate.Downloaded -> {
                    val version = candidate.hostVersionDirectory.version
                    if (setAsideAfterFailedStarts(version)) firstSetAsideVersion = firstSetAsideVersion ?: version
                    verify(candidate.hostVersionDirectory, hostDirectory.readLauncherState()) ?: continue
                }

                is Candidate.Bundled -> candidate.chosenHostVersion
            }
            return recordStartInProgress(chosenHostVersion, firstSetAsideVersion, launchLock)
        }
        log.write("No host version is left to start")
        return LaunchOutcome.NothingLeft
    }

    /**
     * Sets [version] aside when it has failed its last starts; returns whether it did. Only a version
     * that has never completed a start has failures counted.
     */
    private fun setAsideAfterFailedStarts(version: HostVersion): Boolean {
        val state = hostDirectory.readLauncherState()
        if (version in state.setAsideVersions || (state.failedStartCounts[version] ?: 0) < FAILED_STARTS_TO_SET_ASIDE) return false
        log.write("Setting $version aside: it failed its last $FAILED_STARTS_TO_SET_ASIDE starts")
        hostDirectory.writeLauncherState(state.copy(setAsideVersions = state.setAsideVersions + version))
        return true
    }

    private fun candidates(): List<Candidate> {
        val floor = bundledHostVersion?.version
        val downloaded = hostDirectory.hostVersionDirectories()
            .filter { floor == null || it.version > floor }
            .map(Candidate::Downloaded)
        return downloaded + listOfNotNull(bundledHostVersion?.let(Candidate::Bundled))
    }

    private fun verify(hostVersionDirectory: HostVersionDirectory, launcherState: LauncherState): ChosenHostVersion? {
        val version = hostVersionDirectory.version
        if (version in launcherState.setAsideVersions) {
            log.write("Skipping $version: it is set aside")
            return null
        }
        val metadataBytes = readOrNull(hostVersionDirectory.metadataFile)
            ?: return discard(hostVersionDirectory, "it has no ${HostVersionDirectory.METADATA_FILE_NAME}")
        val metadata = when (val read = metadataReader.read(metadataBytes, readOrNull(hostVersionDirectory.signatureFile))) {
            is HostReleaseMetadataResult.Read -> read.metadata

            is HostReleaseMetadataResult.NewerFormat -> {
                log.write("Skipping $version: its metadata has format ${read.format}, which this launcher cannot read")
                return null
            }

            HostReleaseMetadataResult.Untrusted -> return discard(hostVersionDirectory, "its metadata signature does not verify")

            is HostReleaseMetadataResult.Malformed -> return discard(hostVersionDirectory, "its metadata is malformed (${read.reason})")
        }
        if (metadata.version.name != version.name) return discard(hostVersionDirectory, "its metadata is for ${metadata.version}")
        val refusal = metadata.refusalOn(capabilities)
        if (refusal != null) {
            log.write("Skipping $version: $refusal")
            return null
        }
        val jar = hostVersionDirectory.jarFile(capabilities.platformKey)
        if (!hostDirectory.contains(jar)) return discard(hostVersionDirectory, "its jar resolves outside ${hostDirectory.root}")
        val check = try {
            metadata.platforms.getValue(capabilities.platformKey).check(jar)
        } catch (e: IOException) {
            return discard(hostVersionDirectory, "its jar cannot be read (${e.message})")
        }
        if (check != HostJarCheck.Matches) return discard(hostVersionDirectory, "its jar does not match its metadata ($check)")
        return ChosenHostVersion(version = version, metadata = metadata, jar = jar, isBundled = false)
    }

    private fun discard(hostVersionDirectory: HostVersionDirectory, reason: String): ChosenHostVersion? {
        log.write("Deleting ${hostVersionDirectory.version}: $reason")
        delete(hostVersionDirectory)
        return null
    }

    private fun delete(hostVersionDirectory: HostVersionDirectory) {
        if (!hostDirectory.delete(hostVersionDirectory)) log.write("Could not delete all of ${hostVersionDirectory.version}; a later start tries again")
    }

    /**
     * Writes down that this process starts [chosenHostVersion], so that a later launch counts a crash
     * within its startup time window. An `instance.json` a host left behind goes first: that host could
     * have had this process's ID, and its `instance.json` would pass for this host's.
     */
    private fun recordStartInProgress(
        chosenHostVersion: ChosenHostVersion,
        setAsideVersion: HostVersion?,
        launchLock: LaunchLock,
    ): LaunchOutcome.Starting {
        hostDirectory.deleteInstanceJson()
        val state = hostDirectory.readLauncherState()
        hostDirectory.writeLauncherState(
            state.copy(startInProgress = StartInProgress(chosenHostVersion.version, processTable.currentPid, processTable.currentStartMillis)),
        )
        log.write("Starting ${chosenHostVersion.version}${if (chosenHostVersion.isBundled) " (bundled)" else ""}")
        return LaunchOutcome.Starting(HostStart(chosenHostVersion, setAsideVersion, launchLock))
    }

    private fun isInstanceHeldElsewhere(): Boolean {
        val probe = lockFiles.tryLock(hostDirectory.instanceLockFile) ?: return true
        probe.close()
        return false
    }

    /** Counts a failed start of [version], unless it has completed one before: its own crash recovery handles that. */
    private fun failedStart(state: LauncherState, version: HostVersion): LauncherState {
        if (version in state.completedStartVersions) {
            log.write("$version has completed a start before, so this failure is not counted; its crash recovery takes over")
            return state.copy(startInProgress = null)
        }
        val count = (state.failedStartCounts[version] ?: 0) + 1
        log.write("$version has failed $count start(s) in a row")
        return state.copy(failedStartCounts = state.failedStartCounts + (version to count), startInProgress = null)
    }

    private fun readOrNull(file: Path): ByteArray? = try {
        Files.readAllBytes(file)
    } catch (_: IOException) {
        null
    }

    /**
     * The start of [chosenHostVersion] in this process, which records how it ends. The first of three
     * ends counts, and is recorded once, under `launch.lock`: the end of the startup time window, a
     * failure of the host's main, or a shutdown of this JVM. Until then it holds the `launch.lock` the
     * launch took, unless it has been told to let it go.
     *
     * @property setAsideVersion A version this launch set aside, which the host tells the user about.
     */
    inner class HostStart internal constructor(
        val chosenHostVersion: ChosenHostVersion,
        val setAsideVersion: HostVersion?,
        private val launchLock: LaunchLock,
    ) {
        private var isOutcomeRecorded = false

        /** Lets `launch.lock` go until the outcome is recorded, which takes it again for the record. */
        @Synchronized
        fun releaseLaunchLock() {
            if (!isOutcomeRecorded) launchLock.release()
        }

        /**
         * Waits out [startupTimeWindow] from now, which is right before the host's main is called, and
         * then records a completed start, unless the start has ended otherwise by then.
         */
        fun recordCompletedAfter(startupTimeWindow: Duration) {
            sleep(startupTimeWindow)
            recordCompleted()
        }

        /** The host still runs at the end of its startup time window: it completed its start. */
        @Synchronized
        private fun recordCompleted() = recordOutcomeOnce { state ->
            log.write("${chosenHostVersion.version} completed its start")
            pruneAfterStart(
                chosenHostVersion,
                state.copy(
                    completedStartVersions = state.completedStartVersions + chosenHostVersion.version,
                    setAsideVersions = state.setAsideVersions - chosenHostVersion.version,
                    failedStartCounts = state.failedStartCounts - chosenHostVersion.version,
                    startInProgress = null,
                ),
            )
        }

        /** The host's main threw within its startup time window: a failed start. */
        @Synchronized
        fun recordFailed() = recordOutcomeOnce { state ->
            log.write("${chosenHostVersion.version} failed within its startup time window")
            failedStart(state, chosenHostVersion.version)
        }

        /**
         * This JVM shuts down within the startup time window, and the host's main did not throw: the
         * user quit, or the host ended itself, as it does to restart. Neither a failed start nor a
         * completed one.
         */
        @Synchronized
        fun recordEndedWithoutFailure() = recordOutcomeOnce { state ->
            log.write("${chosenHostVersion.version} ended within its startup time window without failing")
            state.copy(startInProgress = null)
        }

        private fun recordOutcomeOnce(stateWithOutcome: (LauncherState) -> LauncherState) {
            if (isOutcomeRecorded) return
            isOutcomeRecorded = true
            launchLock.ensureHeld()
            try {
                hostDirectory.writeLauncherState(stateWithOutcome(hostDirectory.readLauncherState()))
            } finally {
                launchLock.release()
            }
        }
    }

    /**
     * Keeps [chosenHostVersion], which runs, and the newest version newer than it, which is set aside or
     * finished downloading during this start, and deletes every other downloaded version.
     */
    private fun pruneAfterStart(chosenHostVersion: ChosenHostVersion, launcherState: LauncherState): LauncherState {
        val hostVersionDirectories = hostDirectory.hostVersionDirectories()
        val newestNewer = hostVersionDirectories.firstOrNull { it.version > chosenHostVersion.version }
        hostVersionDirectories
            .filterNot { it == newestNewer || (!chosenHostVersion.isBundled && it.version == chosenHostVersion.version) }
            .forEach {
                log.write("Deleting ${it.version}: ${chosenHostVersion.version} completed a start")
                delete(it)
            }
        val keptVersions = hostDirectory.hostVersionDirectories().map(HostVersionDirectory::version).toSet() + listOfNotNull(bundledHostVersion?.version)
        return launcherState.copy(
            completedStartVersions = launcherState.completedStartVersions.intersect(keptVersions),
            setAsideVersions = launcherState.setAsideVersions.intersect(keptVersions),
            failedStartCounts = launcherState.failedStartCounts.filterKeys(keptVersions::contains),
        )
    }

    private sealed interface Candidate {
        data class Downloaded(val hostVersionDirectory: HostVersionDirectory) : Candidate

        data class Bundled(val chosenHostVersion: ChosenHostVersion) : Candidate
    }
}

sealed interface LaunchOutcome {
    /** This process runs [hostStart]'s version, and [hostStart] records how that start goes. */
    class Starting(val hostStart: HostLauncher.HostStart) : LaunchOutcome

    data object BroughtRunningHostToFront : LaunchOutcome

    data object RunningHostUnreachable : LaunchOutcome

    data object NothingLeft : LaunchOutcome
}
