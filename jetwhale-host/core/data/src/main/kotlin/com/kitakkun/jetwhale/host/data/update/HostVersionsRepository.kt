package com.kitakkun.jetwhale.host.data.update

import com.kitakkun.jetwhale.host.model.HostLaunch
import com.kitakkun.jetwhale.host.model.SetAsideHostVersion
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.HostVersionsDirectory
import com.kitakkun.jetwhale.host.release.InstalledHostVersion
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
    private val hostVersionsDirectory = (hostLaunch as? HostLaunch.ByLauncher)?.let { HostVersionsDirectory(it.hostDirectory) }

    /** Newest first. */
    fun installedVersions(): List<InstalledHostVersion> = hostVersionsDirectory?.installedVersions().orEmpty()

    /** The newest installed version the launcher has set aside after it failed its first starts. */
    fun newestSetAsideVersion(): SetAsideHostVersion? {
        val hostVersionsDirectory = hostVersionsDirectory ?: return null
        val setAsideVersions = hostVersionsDirectory.readLauncherState().setAsideVersions
        val version = hostVersionsDirectory.installedVersions().firstOrNull { it.version in setAsideVersions }?.version ?: return null
        return SetAsideHostVersion(version = version, logFile = hostVersionsDirectory.hostLogFile(version))
    }

    /**
     * Deletes every downloaded version but [runningVersion], and any download left half done, before
     * a new download starts. A set-aside version, or one waiting for a restart, is superseded by it.
     */
    fun clearForDownload(runningVersion: HostVersion?) {
        val hostVersionsDirectory = hostVersionsDirectory ?: return
        hostVersionsDirectory.installedVersions().filter { it.version != runningVersion }.forEach(hostVersionsDirectory::delete)
        discardStaging()
    }

    /** An empty directory under `staging/`, which the launcher never reads, to download [version] into. */
    fun newStagingDirectory(version: HostVersion): Path {
        val stagingDirectory = checkNotNull(hostVersionsDirectory) { "Only a host the launcher started downloads versions" }.stagingDirectory.resolve(version.name)
        Files.createDirectories(stagingDirectory)
        return stagingDirectory
    }

    /** Moves a verified download out of `staging/`, so the launcher sees the whole version or none of it. */
    fun install(stagingDirectory: Path, version: HostVersion) {
        val target = checkNotNull(hostVersionsDirectory) { "Only a host the launcher started installs versions" }.root.resolve(version.name)
        try {
            Files.move(stagingDirectory, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(stagingDirectory, target)
        }
    }

    @OptIn(ExperimentalPathApi::class)
    fun discardStaging() {
        hostVersionsDirectory?.stagingDirectory?.deleteRecursively()
    }
}
