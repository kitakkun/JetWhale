package com.kitakkun.jetwhale.host.model

import java.net.URL

/**
 * A plugin as its newest loaded version describes it.
 *
 * @property requiresAgent When false, this is a host-only plugin: available for any active session
 * without negotiation.
 * @property installedVersions Every loaded version of the plugin, newest first.
 */
data class PluginMetaData(
    val name: String,
    val id: String,
    val version: String,
    val installedVersions: List<InstalledPluginVersion>,
    val requiresAgent: Boolean = true,
    val activeIconResource: PluginIconResource? = null,
    val inactiveIconResource: PluginIconResource? = null,
)

/**
 * One loaded version of a plugin and the jar it comes from.
 *
 * @property removable False for a jar outside the managed plugins directory (the dev plugins directory
 *   or a `--plugin-dir`), which the host does not delete.
 */
data class InstalledPluginVersion(
    val version: String,
    val jarPath: String,
    val removable: Boolean,
)

@JvmInline
value class PluginIconResource(val path: URL)
