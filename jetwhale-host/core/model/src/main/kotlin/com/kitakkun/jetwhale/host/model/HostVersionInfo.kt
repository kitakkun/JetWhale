package com.kitakkun.jetwhale.host.model

/**
 * The running host application's own version, provided by the app module from its build metadata.
 * Snapshot builds carry a `-SNAPSHOT` suffix, which official-plugin installation uses to pick the
 * snapshots repository over Maven Central.
 */
data class HostVersionInfo(val version: String) {
    val isSnapshot: Boolean get() = version.endsWith("-SNAPSHOT")
}
