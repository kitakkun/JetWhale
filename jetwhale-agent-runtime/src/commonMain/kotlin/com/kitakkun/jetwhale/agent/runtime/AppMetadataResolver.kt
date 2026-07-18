package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAppMetadata
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Maximum length (in characters) of the base64-encoded app icon that is allowed on the wire.
 * The bound is applied to the encoded string that actually travels in the negotiation payload,
 * so the wire cost stays capped regardless of base64's ~4/3 expansion of the raw PNG bytes.
 * Icons whose encoded form exceeds this are dropped. Senders are expected to downscale icons to
 * at most 64x64 pixels before providing them.
 */
internal const val MAX_APP_ICON_BASE64_LENGTH: Int = 32 * 1024

/**
 * Best-effort resolver for [JetWhaleAppMetadata].
 * Explicit values provided through the DSL always win; anything left unset is filled in
 * with a platform-specific auto-resolved value when one is available.
 */
internal fun resolveAppMetadata(config: ResolvedAppConfiguration): JetWhaleAppMetadata = JetWhaleAppMetadata(
    appName = config.appName ?: resolveDefaultAppName(),
    deviceId = config.deviceId ?: getDeviceId(),
    deviceName = config.deviceName ?: getDeviceModelName(),
    appIconPngBase64 = encodeAppIconOrNull(config.appIconPng),
)

@OptIn(ExperimentalEncodingApi::class)
internal fun encodeAppIconOrNull(png: ByteArray?): String? {
    if (png == null) return null
    val encoded = Base64.encode(png)
    if (encoded.length > MAX_APP_ICON_BASE64_LENGTH) {
        JetWhaleLogger.w("App icon dropped: base64 length ${encoded.length} exceeds the $MAX_APP_ICON_BASE64_LENGTH character cap")
        return null
    }
    return encoded
}

/**
 * Snapshot of the app-related DSL configuration used to resolve [JetWhaleAppMetadata].
 */
internal data class ResolvedAppConfiguration(
    val appName: String?,
    val deviceId: String?,
    val deviceName: String?,
    val appIconPng: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResolvedAppConfiguration) return false
        return appName == other.appName &&
            deviceId == other.deviceId &&
            deviceName == other.deviceName &&
            appIconPng.contentEquals(other.appIconPng)
    }

    override fun hashCode(): Int {
        var result = appName?.hashCode() ?: 0
        result = 31 * result + (deviceId?.hashCode() ?: 0)
        result = 31 * result + (deviceName?.hashCode() ?: 0)
        result = 31 * result + (appIconPng?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Resolves a stable per-device identifier for the current platform, or null when unavailable.
 */
internal expect fun getDeviceId(): String?

/**
 * Resolves the human-readable application name for the current platform, or null when unavailable.
 */
internal expect fun resolveDefaultAppName(): String?
