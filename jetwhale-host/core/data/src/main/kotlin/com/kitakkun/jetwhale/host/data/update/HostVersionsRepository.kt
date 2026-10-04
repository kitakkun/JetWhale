package com.kitakkun.jetwhale.host.data.update

import com.kitakkun.jetwhale.host.model.HostLaunch
import com.kitakkun.jetwhale.host.model.SetAsideHostVersion
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
    private val directory = (hostLaunch as? HostLaunch.ByLauncher)?.let { HostVersionsDirectory(it.hostDirectory) }

    /** Newest first. */
    fun installedVersions(): List<InstalledHostVersion> = directory?.installedVersions().orEmpty()

    /** The newest installed version the launcher has set aside after it failed its first starts. */
    fun setAsideVersion(): SetAsideHostVersion? {
        val directory = directory ?: return null
        val setAside = directory.readLauncherState().setAside
        val version = directory.installedVersions().firstOrNull { it.name in setAside } ?: return null
        return SetAsideHostVersion(version = version.name, log = directory.hostLog(version.name))
    }

    /**
     * Deletes every downloaded version but the running one, and any download left half done, before
     * a new download starts. A set-aside version, or one waiting for a restart, is superseded by it.
     */
    fun clearForDownload(runningVersion: String) {
        val directory = directory ?: return
        directory.installedVersions().filter { it.name != runningVersion }.forEach(directory::delete)
        discardStaging()
    }

    /** An empty directory under `staging/`, which the launcher never reads, to download [version] into. */
    fun newStagingDirectory(version: String): Path {
        val staging = checkNotNull(directory) { "Only a host the launcher started downloads versions" }.staging.resolve(version)
        Files.createDirectories(staging)
        return staging
    }

    /** Moves a verified download out of `staging/`, so the launcher sees the whole version or none of it. */
    fun install(staging: Path, version: String) {
        val target = checkNotNull(directory) { "Only a host the launcher started installs versions" }.root.resolve(version)
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(staging, target)
        }
    }

    @OptIn(ExperimentalPathApi::class)
    fun discardStaging() {
        directory?.staging?.deleteRecursively()
    }
}
