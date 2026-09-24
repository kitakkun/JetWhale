package com.kitakkun.jetwhale.host.model

import java.net.URL

/**
 * @property requiresAgent When false, this is a host-only plugin: available for any active session
 * without negotiation.
 */
data class PluginMetaData(
    val name: String,
    val id: String,
    val version: String,
    val requiresAgent: Boolean = true,
    val activeIconResource: PluginIconResource? = null,
    val inactiveIconResource: PluginIconResource? = null,
)

@JvmInline
value class PluginIconResource(val path: URL)
