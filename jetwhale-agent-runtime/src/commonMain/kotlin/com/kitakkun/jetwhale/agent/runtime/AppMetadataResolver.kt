package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAppMetadata
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Maximum size of an app icon PNG (in bytes) that is allowed on the wire.
 * Icons larger than this are dropped so the negotiation payload stays small.
 * Senders are expected to downscale icons to at most 64x64 pixels before providing them.
 */
internal const val MAX_APP_ICON_BYTES: Int = 32 * 1024

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
    if (png.size > MAX_APP_ICON_BYTES) {
        JetWhaleLogger.w("App icon dropped: ${png.size} bytes exceeds the ${MAX_APP_ICON_BYTES} byte cap")
        return null
    }
    return Base64.encode(png)
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
