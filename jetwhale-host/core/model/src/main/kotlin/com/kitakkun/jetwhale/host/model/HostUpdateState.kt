package com.kitakkun.jetwhale.host.model

import java.nio.file.Path

/**
 * @property setAside An installed version the launcher set aside after it failed its first starts.
 * It is offered as *Try again*, never downloaded again.
 * @property restartFailed *Restart to update* or *Try again* could not start the launcher, so this
 * host kept running.
 */
data class HostUpdateState(
    val status: HostUpdateStatus,
    val setAside: SetAsideHostVersion?,
    val restartFailed: Boolean,
)

/** @property log The output of the version's last start, which shows why it failed. */
data class SetAsideHostVersion(
    val version: String,
    val log: Path,
)

sealed interface HostUpdateStatus {
    /** Not started by the launcher, so the host installs nothing and only links to the releases. */
    data object NotManaged : HostUpdateStatus

    data object NotChecked : HostUpdateStatus

    data object Checking : HostUpdateStatus

    data object UpToDate : HostUpdateStatus

    /** A newer release this launcher can run, offered as a download of [sizeBytes]. */
    data class Available(val version: String, val sizeBytes: Long) : HostUpdateStatus

    data class Downloading(val version: String, val downloadedBytes: Long, val totalBytes: Long) : HostUpdateStatus

    /** A newer version is installed; the next start runs it. */
    data class ReadyToRestart(val version: String) : HostUpdateStatus

    /** The newest release needs a newer launcher or runtime than this install has. */
    data class NeedsNewInstaller(val version: String) : HostUpdateStatus

    /** The newest release has no host jar for this operating system and processor. */
    data class NoBuildForThisComputer(val version: String) : HostUpdateStatus

    /** Looking up the newest release failed. */
    data class CheckFailed(val failure: HostUpdateFailure) : HostUpdateStatus

    /** Downloading or installing a release failed; nothing was installed. */
    data class DownloadFailed(val failure: HostUpdateFailure) : HostUpdateStatus
}

sealed interface HostUpdateFailure {
    /** GitHub's limit of unauthenticated API requests per address is used up. */
    data object RateLimited : HostUpdateFailure

    data object Unreachable : HostUpdateFailure

    data class UnexpectedResponse(val statusCode: Int) : HostUpdateFailure

    /** The release's metadata did not pass the signature check, or could not be read. */
    data class BadMetadata(val reason: String) : HostUpdateFailure

    /** The downloaded jar does not match the size and SHA-256 its release's metadata pins. */
    data object Corrupted : HostUpdateFailure

    /** Writing the download to this computer's disk failed, as it does when the disk is full. */
    data object CouldNotSave : HostUpdateFailure
}
