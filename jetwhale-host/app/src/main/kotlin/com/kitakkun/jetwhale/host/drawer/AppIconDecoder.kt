package com.kitakkun.jetwhale.host.drawer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.util.Base64

/**
 * Maximum accepted length of a base64-encoded app icon. Matches the cap agents enforce before
 * sending, so a buggy or malicious agent cannot make the host decode an unbounded payload.
 */
private const val MAX_APP_ICON_BASE64_LENGTH: Int = 32 * 1024

/**
 * Decodes a base64-encoded PNG app icon into an [ImageBitmap].
 * Returns null when the payload is missing, exceeds the size cap, or cannot be decoded.
 */
internal fun decodeIconOrNull(base64Png: String): ImageBitmap? {
    if (base64Png.length > MAX_APP_ICON_BASE64_LENGTH) return null
    return try {
        val bytes = Base64.getDecoder().decode(base64Png)
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: Throwable) {
        null
    }
}
