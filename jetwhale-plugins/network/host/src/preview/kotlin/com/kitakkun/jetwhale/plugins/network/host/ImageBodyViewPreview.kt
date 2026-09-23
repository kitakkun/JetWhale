package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme

/** A 1x1 PNG, so the preview shows the real decode-and-draw path rather than the text fallback. */
private const val ONE_PIXEL_PNG =
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="

@Preview
@Composable
private fun ImageBodyBlockPreview() {
    JwTheme(darkTheme = false) {
        ImageBodyBlock(
            body = ONE_PIXEL_PNG,
            mediaType = "image/png",
            url = "https://example.com/assets/logo.png",
            truncated = false,
        )
    }
}
