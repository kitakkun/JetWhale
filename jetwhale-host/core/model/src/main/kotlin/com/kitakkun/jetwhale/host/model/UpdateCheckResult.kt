package com.kitakkun.jetwhale.host.model

/**
 * Outcome of querying the update site. Versions are in Conveyor's purely numeric form
 * (e.g. `1.0.0.8` for `1.0.0-alpha08`), matching what the packages themselves carry.
 */
data class UpdateCheckResult(
    val currentVersion: String,
    val latestVersion: String,
    val updateAvailable: Boolean,
    val downloadPageUrl: String,
)
