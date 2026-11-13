package com.kitakkun.jetwhale.host.model

import java.net.URL

/**
 * @property requiresAgent Whether the plugin runs in the app sessions whose agent advertised it. When
 *   false, this is a host-only plugin, which runs in [HostSession] only.
 * @property targetsHostSession Whether the plugin has an instance in [HostSession]: a host-only
 *   plugin, or one that runs there as well as in app sessions.
 */
data class PluginMetaData(
    val name: String,
    val id: String,
    val version: String,
    val requiresAgent: Boolean = true,
    val targetsHostSession: Boolean,
    val activeIconResource: PluginIconResource? = null,
    val inactiveIconResource: PluginIconResource? = null,
)

@JvmInline
value class PluginIconResource(val path: URL)
