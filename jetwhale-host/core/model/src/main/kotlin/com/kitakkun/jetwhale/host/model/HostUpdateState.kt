package com.kitakkun.jetwhale.host.model

import java.nio.file.Path

/**
 * @property setAside An installed version the launcher set aside after it failed its first starts.
 * It is offered as *Try again*, never downloaded again.
 */
data class HostUpdateState(
    val status: HostUpdateStatus,
    val setAside: SetAsideHostVersion?,
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

    data class Failed(val failure: HostUpdateFailure) : HostUpdateStatus
}

sealed interface HostUpdateFailure {
    /** GitHub's limit of unauthenticated API requests per address is used up. */
    data object RateLimited : HostUpdateFailure

    data class Unreachable(val message: String) : HostUpdateFailure

    data class UnexpectedResponse(val statusCode: Int) : HostUpdateFailure

    /** The release's metadata did not pass the signature check, or could not be read. */
    data class BadMetadata(val reason: String) : HostUpdateFailure

    /** The downloaded jar does not match the size and SHA-256 its release's metadata pins. */
    data object Corrupted : HostUpdateFailure
}
