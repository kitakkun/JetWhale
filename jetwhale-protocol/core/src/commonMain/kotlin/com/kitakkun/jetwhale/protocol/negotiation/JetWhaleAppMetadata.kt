package com.kitakkun.jetwhale.protocol.negotiation

import com.kitakkun.jetwhale.protocol.JetWhaleSerialNames
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Metadata that identifies the debuggee application and the device it runs on.
 *
 * This is exchanged additively during session negotiation so the host can group
 * sessions by device and by application. Every field is optional: agents that
 * cannot resolve a value (or predate this metadata) simply omit it, and the host
 * falls back to other identifiers such as [JetWhaleAgentNegotiationRequest.Session.sessionName].
 *
 * @param appName Human-readable application name (e.g. the Android launcher label or the iOS bundle name).
 * @param deviceId Stable per-device identifier used to group sessions coming from the same device.
 * @param deviceName Human-readable device name (e.g. `Build.MODEL`, `UIDevice.name`, or the machine hostname).
 * @param appIconPngBase64 Base64-encoded PNG of the application icon. Senders MUST downscale to at most
 *   64x64 pixels and skip the icon entirely when the encoded PNG would exceed 32KB, so hosts can rely on
 *   the payload staying small.
 */
@SerialName(JetWhaleSerialNames.MODEL_APP_METADATA)
@Serializable
public data class JetWhaleAppMetadata(
    val appName: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val appIconPngBase64: String? = null,
)
