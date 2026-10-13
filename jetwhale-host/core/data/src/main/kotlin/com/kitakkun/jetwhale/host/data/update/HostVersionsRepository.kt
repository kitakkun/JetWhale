package com.kitakkun.jetwhale.host.data.update

import com.kitakkun.jetwhale.host.model.HostLaunch
import com.kitakkun.jetwhale.host.model.SetAsideHostVersion
import com.kitakkun.jetwhale.host.release.HostDirectory
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.HostVersionDirectory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/**
 * The version directories under `<app data>/host/` that the launcher starts from. The host writes
 * downloads there and reads the launcher's records; it never writes those records, which the
 * launcher alone keeps.
 */
@Inject
@SingleIn(AppScope::class)
class HostVersionsRepository(hostLaunch: HostLaunch) {
    private val hostDirectory = (hostLaunch as? HostLaunch.ByLauncher)?.let { HostDirectory(it.hostDirectoryPath) }

    /** Newest first. */
    fun hostVersionDirectories(): List<HostVersionDirectory> = hostDirectory?.hostVersionDirectories().orEmpty()

    /** The newest installed version the launcher has set aside after it failed its first starts. */
    fun newestSetAsideVersion(): SetAsideHostVersion? {
        val hostDirectory = hostDirectory ?: return null
        val setAsideVersions = hostDirectory.readLauncherState().setAsideVersions
        val version = hostDirectory.hostVersionDirectories().firstOrNull { it.version in setAsideVersions }?.version ?: return null
        return SetAsideHostVersion(version = version, logFile = hostDirectory.hostLogFile(version))
    }

    /**
     * Deletes every downloaded version but [runningVersion], and any download left half done, before
     * a new download starts. A set-aside version, or one waiting for a restart, is superseded by it.
     */
    fun clearForDownload(runningVersion: HostVersion?) {
        val hostDirectory = hostDirectory ?: return
        hostDirectory.hostVersionDirectories().filter { it.version != runningVersion }.forEach(hostDirectory::delete)
        discardStaging()
    }

    /** An empty directory under `staging/`, which the launcher never reads, to download [version] into. */
    fun newStagingDirectory(version: HostVersion): Path {
        val stagingDirectory = checkNotNull(hostDirectory) { "Only a host the launcher started downloads versions" }.stagingDirectory.resolve(version.name)
        Files.createDirectories(stagingDirectory)
        return stagingDirectory
    }

    /** Moves a verified download out of `staging/`, so the launcher sees the whole version or none of it. */
    fun install(stagingDirectory: Path, version: HostVersion) {
        val target = checkNotNull(hostDirectory) { "Only a host the launcher started installs versions" }.root.resolve(version.name)
        try {
            Files.move(stagingDirectory, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(stagingDirectory, target)
        }
    }

    @OptIn(ExperimentalPathApi::class)
    fun discardStaging() {
        hostDirectory?.stagingDirectory?.deleteRecursively()
    }
}
