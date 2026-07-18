package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import kotlinx.collections.immutable.ImmutableList

data class DebugSession(
    val id: String,
    val name: String?,
    val isActive: Boolean,
    /** Security of the transport carrying this session. */
    val transportSecurity: SessionTransportSecurity,
    val installedPlugins: ImmutableList<JetWhalePluginInfo>,
    val appName: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val appIconPngBase64: String? = null,
) {
    private val shortId: String
        get() = id.take(6)

    val displayName: String
        get() = name?.let { "$it ($shortId)" } ?: shortId

    /**
     * Identifier used to group sessions by device. Falls back to the session id when no
     * stable device id was negotiated, so ungrouped sessions still appear as their own device.
     */
    val groupingDeviceId: String
        get() = deviceId ?: id

    /**
     * Human-readable device label. Prefers the negotiated device name, then the session name.
     */
    val deviceDisplayName: String
        get() = deviceName ?: name ?: shortId

    /**
     * Human-readable application label. Prefers the negotiated app name, then the session display name.
     */
    val appDisplayName: String
        get() = appName ?: displayName
}
