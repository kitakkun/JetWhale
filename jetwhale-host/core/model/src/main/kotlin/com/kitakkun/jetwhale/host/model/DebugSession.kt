package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import kotlinx.collections.immutable.ImmutableList

/**
 * @property transportSecurity Security of the transport carrying this session.
 */
data class DebugSession(
    val id: String,
    val name: String?,
    val isActive: Boolean,
    val transportSecurity: SessionTransportSecurity,
    val installedPlugins: ImmutableList<JetWhalePluginInfo>,
    val appName: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val appIconPngBase64: String? = null,
) {
    /** The first characters of [id], enough to tell sessions apart on screen. */
    val shortId: String
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
     * The app's own name: the negotiated app name, else the session name when it says something the
     * device name does not. Null when neither names the app; a desktop agent reports no app name by
     * default and names the session after the machine, which would read as the device twice.
     */
    val appLabel: String?
        get() = appName ?: name?.takeIf { it != deviceDisplayName }

    /** Human-readable application label: [appLabel], else [shortId] to tell unnamed apps apart. */
    val appDisplayName: String
        get() = appLabel ?: shortId

    /** Device and app labels joined, for places that identify a session on a single line. */
    val deviceAndAppDisplayName: String
        get() = "$deviceDisplayName · $appDisplayName"
}
