package com.kitakkun.jetwhale.host.release

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * `<app data>/host/`: the host versions the host downloaded, one directory each, and what the
 * launcher and the running host record next to them.
 *
 * ```
 * 1.0.0-alpha15/       jetwhale-host-1.0.0-alpha15-macos-arm64.jar, release.json, release.json.sig
 * staging/             downloads in progress; the launcher never reads it
 * launcher-state.json  which versions completed a start, failed or are set aside, and the start in progress
 * launch.lock, instance.lock, instance.json
 * ```
 */
class HostVersionsDirectory(val root: Path) {
    val stagingDirectory: Path get() = root.resolve("staging")

    /** Held by the one launcher choosing and starting a host. */
    val launchLockFile: Path get() = root.resolve("launch.lock")

    /** Held by the running host for as long as it runs. */
    val instanceLockFile: Path get() = root.resolve("instance.lock")

    /** The running host's [HostInstanceRecord]. */
    val instanceRecordFile: Path get() = root.resolve("instance.json")

    private val launcherStateFile: Path get() = root.resolve("launcher-state.json")

    /** `<app data>/logs`, where the launcher writes its own log and the output of each host start. */
    val logsDirectory: Path get() = root.resolveSibling("logs")

    /** The output of [version]'s last GUI start, which the host links to when that version was set aside. */
    fun hostLogFile(version: HostVersion): Path = logsDirectory.resolve("host-${version.name}.log")

    /** The version directories, newest first. A directory not named for a release version is not one. */
    fun installedVersions(): List<InstalledHostVersion> {
        if (!root.isDirectory()) return emptyList()
        return root.listDirectoryEntries()
            .filter { it.isDirectory() }
            .mapNotNull { directory ->
                HostVersion.parse(directory.name)?.let { InstalledHostVersion(it, directory) }
            }
            .sortedByDescending(InstalledHostVersion::version)
    }

    fun installedVersion(versionName: String): InstalledHostVersion? {
        val version = HostVersion.parse(versionName) ?: return null
        val directory = root.resolve(versionName)
        return if (directory.isDirectory()) InstalledHostVersion(version, directory) else null
    }

    /** What the launcher has recorded, or nothing when the file is missing or unreadable. */
    fun readLauncherState(): LauncherState = try {
        json.decodeFromString(LauncherState.serializer(), Files.readString(launcherStateFile))
    } catch (_: IOException) {
        LauncherState.EMPTY
    } catch (_: SerializationException) {
        LauncherState.EMPTY
    }

    /**
     * Replaces `launcher-state.json`. Only the launcher writes it, and only while it holds
     * [launchLockFile]; the host only reads it.
     */
    fun writeLauncherState(state: LauncherState) {
        writeAtomically(launcherStateFile, json.encodeToString(LauncherState.serializer(), state))
    }

    /**
     * Deletes [installedVersion]'s directory, or the link that stands in for one, never what a link
     * points to. Returns false when something could not be deleted, as a running host's jar cannot on
     * Windows; it stays until a later start.
     */
    @OptIn(ExperimentalPathApi::class)
    fun delete(installedVersion: InstalledHostVersion): Boolean = try {
        installedVersion.directory.deleteRecursively()
        true
    } catch (_: IOException) {
        false
    }

    /**
     * Deletes the instance record a host left behind when it ended. A later host can get the same
     * process ID, and the old record would then pass for its own.
     */
    fun deleteInstanceRecord() {
        Files.deleteIfExists(instanceRecordFile)
    }

    /** Whether [file] resolves, through any links, to a place inside this directory. */
    fun contains(file: Path): Boolean = try {
        file.toRealPath().startsWith(root.toRealPath())
    } catch (_: IOException) {
        false
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Writes [text] to a temporary file next to [target] and renames it over [target], so a
         * reader sees the old content or the new, never a part.
         */
        fun writeAtomically(target: Path, text: String) {
            Files.createDirectories(target.parent)
            val temporary = Files.createTempFile(target.parent, target.name, ".tmp")
            try {
                Files.writeString(temporary, text)
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }
}

/** A version directory: the host jar of each platform it was downloaded for, and its metadata. */
class InstalledHostVersion(val version: HostVersion, val directory: Path) {
    val metadataFile: Path get() = directory.resolve(METADATA_FILE_NAME)

    val signatureFile: Path get() = directory.resolve(SIGNATURE_FILE_NAME)

    fun jarFile(platformKey: String): Path = directory.resolve(hostJarName(version, platformKey))

    companion object {
        const val METADATA_FILE_NAME = "release.json"
        const val SIGNATURE_FILE_NAME = "release.json.sig"
    }
}

/** The name a release gives the host jar of [platformKey], in its assets and in a version directory. */
fun hostJarName(version: HostVersion, platformKey: String): String = "jetwhale-host-${version.name}-$platformKey.jar"

/**
 * @property completedStartVersions The versions that were still running at the end of a startup
 * window on this machine. Such a version is never set aside again.
 * @property setAsideVersions The versions that failed their first starts, until the user tries them
 * again. An entry for a version whose directory is gone means nothing.
 * @property failedStartCounts How many starts in a row each version that has not completed one has
 * failed.
 * @property startingHost The host whose start has not been judged yet: it is in its startup window,
 * or its process ended without the judgment being recorded.
 */
@Serializable
data class LauncherState(
    val completedStartVersions: Set<HostVersion>,
    val setAsideVersions: Set<HostVersion>,
    val failedStartCounts: Map<HostVersion, Int>,
    val startingHost: StartingHost?,
) {
    companion object {
        val EMPTY = LauncherState(
            completedStartVersions = emptySet(),
            setAsideVersions = emptySet(),
            failedStartCounts = emptyMap(),
            startingHost = null,
        )
    }
}

/**
 * A host start the launcher wrote down before running [version] in the process [pid].
 *
 * @property processStartMillis When that process started, in milliseconds since the epoch, which
 * tells it from a later process that got the same ID; null when the OS did not say.
 */
@Serializable
data class StartingHost(
    val version: HostVersion,
    val pid: Long,
    val processStartMillis: Long?,
)
