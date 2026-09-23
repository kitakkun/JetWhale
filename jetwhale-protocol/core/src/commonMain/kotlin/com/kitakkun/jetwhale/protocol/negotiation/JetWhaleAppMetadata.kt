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
 *   64x64 pixels and skip the icon entirely when the base64-encoded string would exceed 32KB (the cap is
 *   applied to the encoded payload, not the raw PNG bytes), so hosts can rely on the payload staying small.
 */
@SerialName(JetWhaleSerialNames.MODEL_APP_METADATA)
@Serializable
public class JetWhaleAppMetadata(
    public val appName: String? = null,
    public val deviceId: String? = null,
    public val deviceName: String? = null,
    public val appIconPngBase64: String? = null,
) {
    override fun equals(other: Any?): Boolean = other is JetWhaleAppMetadata &&
        appName == other.appName &&
        deviceId == other.deviceId &&
        deviceName == other.deviceName &&
        appIconPngBase64 == other.appIconPngBase64

    override fun hashCode(): Int {
        var result = appName.hashCode()
        result = 31 * result + deviceId.hashCode()
        result = 31 * result + deviceName.hashCode()
        result = 31 * result + appIconPngBase64.hashCode()
        return result
    }

    override fun toString(): String = "JetWhaleAppMetadata(appName=$appName, deviceId=$deviceId, " +
        "deviceName=$deviceName, appIconPngBase64=$appIconPngBase64)"
}
