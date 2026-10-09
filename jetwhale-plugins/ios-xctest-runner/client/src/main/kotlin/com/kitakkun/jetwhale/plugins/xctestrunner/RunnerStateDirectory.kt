package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import kotlin.time.Duration

/**
 * What `<udid>.json` holds about the runner driving that device, for any plugin that wants to use it.
 *
 * @property pid the runner's `xcodebuild`, which lives as long as the runner's test.
 * @property port where it answers on this machine's loopback; for a device, the forward to it.
 * @property token what each request must carry.
 * @property developmentTeam the team a device's runner is signed for; null for a simulator.
 */
@Serializable
internal data class RunnerState(
    val protocolVersion: Int,
    val pid: Long,
    val port: Int,
    val token: String,
    val developmentTeam: String?,
)

private val StateJson = Json { ignoreUnknownKeys = true }

/**
 * The directory where runners are recorded, one `<udid>.json` per running runner, beside a
 * `<udid>.lock` that whoever starts or replaces that device's runner holds. Every plugin that drives
 * iOS reads the same directory, which is how one runner per device is shared between plugins whose
 * class loaders share nothing else.
 */
internal class RunnerStateDirectory(private val directory: File) {
    fun readRunnerState(udid: String): RunnerState? {
        val file = stateFileOf(udid)
        if (!file.isFile) return null
        return try {
            StateJson.decodeFromString<RunnerState>(file.readText())
        } catch (_: SerializationException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    /** Records [state] at once, readable by this user only: it holds the runner's token. */
    fun writeRunnerState(udid: String, runnerState: RunnerState) {
        directory.mkdirs()
        val staging = File(directory, "$udid.json.partial").toPath()
        Files.deleteIfExists(staging)
        try {
            Files.createFile(staging, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        } catch (_: UnsupportedOperationException) {
            // Only a file system without POSIX permissions throws this, and runners exist on macOS
            // only.
        }
        Files.writeString(staging, StateJson.encodeToString(RunnerState.serializer(), runnerState))
        Files.move(staging, stateFileOf(udid).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /**
     * Forgets the runner of [udid], unless the record is already another runner's than [pid]'s. Hold
     * [withDeviceLock] around it, or another plugin may record a new runner between the check and
     * the deletion.
     */
    fun deleteRunnerState(udid: String, pid: Long) {
        if (readRunnerState(udid)?.pid == pid) stateFileOf(udid).delete()
    }

    /** Runs [block] holding [udid]'s lock, so that no other plugin starts or replaces its runner meanwhile. */
    suspend fun <T> withDeviceLock(udid: String, timeout: Duration, block: suspend () -> T): T = withFileLock(File(directory, "$udid.lock"), timeout, block)

    private fun stateFileOf(udid: String) = File(directory, "$udid.json")
}
