package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import kotlinx.collections.immutable.ImmutableList

data class DebugSession(
    val id: String,
    val name: String?,
    val isActive: Boolean,
    val installedPlugins: ImmutableList<JetWhalePluginInfo>,
) {
    private val shortId: String
        get() = id.take(6)

    val displayName: String
        get() = name?.let { "$it ($shortId)" } ?: shortId
}
