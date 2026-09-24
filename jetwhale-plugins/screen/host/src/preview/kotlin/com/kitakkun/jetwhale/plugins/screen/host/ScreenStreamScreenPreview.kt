package com.kitakkun.jetwhale.plugins.screen.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme

@Preview
@Composable
private fun ScreenStreamScreenPreview() {
    JwTheme(darkTheme = false) {
        ScreenStreamScreen(
            frame = null,
            stats = StreamStatsSnapshot(
                frames = 120,
                framesPerSecond = 24.0,
                averageLatencyMillis = 48.0,
                p95LatencyMillis = 71,
                averageFrameBytes = 61_000.0,
                kilobytesPerSecond = 1_430.0,
                averageMainThreadMicros = 180.0,
                averagePixelCopyMillis = 9.0,
                averageComposeMillis = 2.0,
                averageCompressMillis = 11.0,
                averageBase64Millis = 1.0,
                averageTransportMillis = 3.0,
                averageDecodeMillis = 4.0,
            ),
            status = "Streaming at 50%, quality 70, up to 30 fps.",
            streaming = true,
            settings = StreamSettings(scale = 0.5f, jpegQuality = 70, maxFramesPerSecond = 30),
            onSettingsChange = {},
            onStart = {},
            onStop = {},
        )
    }
}
