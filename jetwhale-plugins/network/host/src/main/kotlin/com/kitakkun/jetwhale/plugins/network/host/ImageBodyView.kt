package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwPanel
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import java.util.Locale
import org.jetbrains.skia.Image as SkiaImage

/** Tall enough to judge an image at a glance without pushing the rest of the detail pane offscreen. */
private val PreviewMaxHeight = 320.dp

/**
 * Renders an image body as a picture, with the actions a developer reaches for next: copying it to
 * the clipboard and saving it to disk.
 *
 * [body] is the Base64 capture from the agent. A body that does not decode (a format Skia cannot
 * read, or a capture cut short) falls back to the text rendering so the raw payload is still there.
 */
@Composable
internal fun ImageBodyBlock(body: String, mediaType: String?, url: String, truncated: Boolean) {
    val image = remember(body) { decodeImageBody(body) }
    if (image == null) {
        BodyBlock(label = "body", body = body, truncated = truncated)
        return
    }
    var status by remember(body) { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.small),
    ) {
        JwPanel(contentPadding = PaddingValues(JwSpacing.large)) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = image.bitmap,
                    contentDescription = "Response image preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.heightIn(max = PreviewMaxHeight),
                )
            }
        }
        JwText(
            text = "${mediaType ?: "image"} • ${image.bitmap.width}×${image.bitmap.height} • ${formatByteSize(image.bytes.size)}",
            style = JwTheme.textStyles.labelSmall,
            color = JwTheme.colors.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
            JwButton(
                text = "Copy image",
                onClick = {
                    status = if (copyImageToClipboard(image.bitmap)) "Copied to clipboard" else "Could not copy the image"
                },
            )
            JwButton(
                text = "Save image…",
                onClick = {
                    saveImageToFile(image.bytes, suggestedImageFileName(url, mediaType)) { status = it }
                },
            )
        }
        status?.let {
            JwText(text = it, style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary)
        }
    }
}

/** A decoded image body: the original bytes (what gets saved) alongside the bitmap to draw. */
internal class DecodedImage(val bytes: ByteArray, val bitmap: ImageBitmap)

/** Decodes a Base64 image body, or null when it is not Base64 or not a format Skia can read. */
internal fun decodeImageBody(body: String): DecodedImage? = runCatching {
    val bytes = base64Decode(body)
    DecodedImage(bytes, SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap())
}.getOrNull()

/** Names a downloaded image after the URL's last path segment, falling back to the media type. */
internal fun suggestedImageFileName(url: String, mediaType: String?): String {
    val segment = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
    val name = segment.substringBeforeLast('.', segment).replace(UNSAFE_FILE_NAME_CHARS, "_").ifBlank { "image" }
    return "$name.${imageFileExtension(mediaType)}"
}

private val UNSAFE_FILE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")

private fun imageFileExtension(mediaType: String?): String = when (mediaType) {
    // jpg over the media type's own "jpeg": it is what image tools and users expect to see.
    "image/jpeg" -> "jpg"

    "image/x-icon", "image/vnd.microsoft.icon" -> "ico"

    null -> "img"

    // Covers image/webp, image/avif, ... whose subtype is already the conventional extension.
    else -> mediaType.substringAfter('/').substringAfterLast('+').removePrefix("x-").ifBlank { "img" }
}

internal fun formatByteSize(bytes: Int): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
}
