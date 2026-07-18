package com.kitakkun.jetwhale.host.drawer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.util.Base64

/**
 * Decodes a base64-encoded PNG app icon into an [ImageBitmap].
 * Returns null when the payload is missing or cannot be decoded.
 */
internal fun decodeIconOrNull(base64Png: String): ImageBitmap? = try {
    val bytes = Base64.getDecoder().decode(base64Png)
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (_: Throwable) {
    null
}
