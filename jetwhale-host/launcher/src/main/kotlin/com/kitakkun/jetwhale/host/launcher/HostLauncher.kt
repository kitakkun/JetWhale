package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HeldLock
import com.kitakkun.jetwhale.host.release.HostDirectory
import com.kitakkun.jetwhale.host.release.HostJarCheck
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataReader
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataResult
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.HostVersionDirectory
import com.kitakkun.jetwhale.host.release.LauncherCapabilities
import com.kitakkun.jetwhale.host.release.LauncherCompatibility
import com.kitakkun.jetwhale.host.release.LauncherState
import com.kitakkun.jetwhale.host.release.LockFiles
import com.kitakkun.jetwhale.host.release.StartedHostProcess
import com.kitakkun.jetwhale.host.release.check
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration

/** How many starts in a row a version that has never completed one may fail before it is set aside. */
private const val FAILED_STARTS_TO_SET_ASIDE = 2

/**
 * Chooses the host version this process runs, and writes its start down.
 *
 * Under `launch.lock`, it first judges a start that an earlier launch wrote down and never judged: when
 * that process has ended, it crashed or was killed within its startup time window, which is a failed
 * start. Then it hands the launch to a host that is already running. Otherwise it goes through the
 * downloaded versions newer than the bundled one, newest first, then the bundled one, and picks the
 * first that is not set aside, not refused and verifies. A downloaded version that has never completed
 * a start and has failed its last two is set aside on the way.
 *
 * @param bundledHostJar Null when the launcher runs outside a package, which has no bundled host.
 * @param processTable This process, which runs the chosen host, and those of earlier launches.
 */
class HostLauncher(
    private val hostDirectory: HostDirectory,
    private val bundledHostJar: ChosenHostJar?,
    private val capabilities: LauncherCapabilities,
    private val metadataReader: HostReleaseMetadataReader,
    private val lockFiles: LockFiles,
    private val runningHostChannel: RunningHostChannel,
    private val processTable: ProcessTable,
    private val log: LauncherLog,
    private val sleep: (Duration) -> Unit,
) {
    private val launcherCompatibility = LauncherCompatibility(capabilities)

    private val hostVersionRetention = HostVersionRetention(hostDirectory, bundledHostJar?.version, log)

    private val failedStartCounter = FailedStartCounter(log)

    /**
     * Returns [LaunchOutcome.Starting] still holding `launch.lock`, which its [HostStartOutcomeRecorder]
     * lets go.
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
        val launchLock = lockFiles.lock(hostDirectory.launchLockFile)
        var handedOver = false
        try {
            judgeEndedStart()
            if (retryVersion != null) clearSetAside(retryVersion)
            if (isInstanceHeldElsewhere()) return handToRunningHost()
            val outcome = chooseAndStart(launchLock)
            handedOver = outcome is LaunchOutcome.Starting
            return outcome
        } finally {
            if (!handedOver) launchLock.close()
        }
    }

    /**
     * A start still written down while its process no longer runs ended without this launcher's
     * shutdown hook or failure handling: a crash, an hs_err, or a kill.
     */
    private fun judgeEndedStart() {
        val state = hostDirectory.readLauncherState()
        val startedHostProcess = state.startedHostProcess ?: return
        if (processTable.isRunning(startedHostProcess.pid, startedHostProcess.processStartMillis)) return
        log.write("${startedHostProcess.version} ended within its startup time window without a record of how: a crash or a kill")
        hostDirectory.writeLauncherState(failedStartCounter.countFailedStart(state, startedHostProcess.version))
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

    private fun chooseAndStart(launchLock: HeldLock): LaunchOutcome {
        var firstSetAsideVersion: HostVersion? = null
        for (candidate in candidates()) {
            val chosenHostJar = when (candidate) {
                is Candidate.Downloaded -> {
                    val version = candidate.hostVersionDirectory.version
                    if (setAsideAfterFailedStarts(version)) firstSetAsideVersion = firstSetAsideVersion ?: version
                    verify(candidate.hostVersionDirectory, hostDirectory.readLauncherState()) ?: continue
                }

                is Candidate.Bundled -> candidate.chosenHostJar
            }
            return recordStartedHostProcess(chosenHostJar, firstSetAsideVersion, launchLock)
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
        val floor = bundledHostJar?.version
        val downloaded = hostDirectory.hostVersionDirectories()
            .filter { floor == null || it.version > floor }
            .map(Candidate::Downloaded)
        return downloaded + listOfNotNull(bundledHostJar?.let(Candidate::Bundled))
    }

    private fun verify(hostVersionDirectory: HostVersionDirectory, launcherState: LauncherState): ChosenHostJar? {
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
        val refusal = launcherCompatibility.refusalOf(metadata)
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
        return ChosenHostJar(version = version, metadata = metadata, path = jar, isBundled = false)
    }

    private fun discard(hostVersionDirectory: HostVersionDirectory, reason: String): ChosenHostJar? {
        log.write("Deleting ${hostVersionDirectory.version}: $reason")
        if (!hostDirectory.delete(hostVersionDirectory)) log.write("Could not delete all of ${hostVersionDirectory.version}; a later start tries again")
        return null
    }

    /**
     * Writes down that this process starts [chosenHostJar], so that a later launch counts a crash
     * within its startup time window. An `instance.json` a host left behind goes first: that host could
     * have had this process's ID, and its `instance.json` would pass for this host's.
     */
    private fun recordStartedHostProcess(
        chosenHostJar: ChosenHostJar,
        setAsideVersion: HostVersion?,
        launchLock: HeldLock,
    ): LaunchOutcome.Starting {
        hostDirectory.deleteInstanceJson()
        val state = hostDirectory.readLauncherState()
        hostDirectory.writeLauncherState(
            state.copy(startedHostProcess = StartedHostProcess(chosenHostJar.version, processTable.currentPid, processTable.currentStartMillis)),
        )
        log.write("Starting ${chosenHostJar.version}${if (chosenHostJar.isBundled) " (bundled)" else ""}")
        val startOutcomeRecorder = HostStartOutcomeRecorder(
            chosenHostJar = chosenHostJar,
            launchLock = launchLock,
            hostDirectory = hostDirectory,
            lockFiles = lockFiles,
            hostVersionRetention = hostVersionRetention,
            failedStartCounter = failedStartCounter,
            log = log,
            sleep = sleep,
        )
        return LaunchOutcome.Starting(chosenHostJar, setAsideVersion, startOutcomeRecorder)
    }

    private fun isInstanceHeldElsewhere(): Boolean {
        val probe = lockFiles.tryLock(hostDirectory.instanceLockFile) ?: return true
        probe.close()
        return false
    }

    private fun readOrNull(file: Path): ByteArray? = try {
        Files.readAllBytes(file)
    } catch (_: IOException) {
        null
    }

    private sealed interface Candidate {
        data class Downloaded(val hostVersionDirectory: HostVersionDirectory) : Candidate

        data class Bundled(val chosenHostJar: ChosenHostJar) : Candidate
    }
}
