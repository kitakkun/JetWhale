package com.kitakkun.jetwhale.plugins.screen.host

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwSegmentedButtons
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwToolbar
import java.util.Locale

private val Scales = listOf(0.25f, 0.5f, 1f)
private val Qualities = listOf(50, 70, 90)
private val FrameRates = listOf(10, 30, 60)

/** How the next stream is started, and — while one runs — how the running one was. */
internal data class StreamSettings(val scale: Float, val jpegQuality: Int, val maxFramesPerSecond: Int)

@Composable
internal fun ScreenStreamScreen(
    frame: ImageBitmap?,
    stats: StreamStatsSnapshot?,
    status: String?,
    streaming: Boolean,
    settings: StreamSettings,
    onSettingsChange: (StreamSettings) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        JwToolbar(
            title = "Screen",
            actions = {
                if (streaming) {
                    JwButton(text = "Stop", onClick = onStop, style = JwButtonStyle.Text)
                } else {
                    JwButton(text = "Start", onClick = onStart, style = JwButtonStyle.Text)
                }
            },
        )
        Row(Modifier.padding(JwSpacing.medium), horizontalArrangement = Arrangement.spacedBy(JwSpacing.large), verticalAlignment = Alignment.CenterVertically) {
            JwSegmentedButtons(options = Scales, selected = settings.scale, onSelect = { onSettingsChange(settings.copy(scale = it)) }, label = { "${(it * 100).toInt()}%" }, enabled = !streaming)
            JwSegmentedButtons(options = Qualities, selected = settings.jpegQuality, onSelect = { onSettingsChange(settings.copy(jpegQuality = it)) }, label = { "q$it" }, enabled = !streaming)
            JwSegmentedButtons(options = FrameRates, selected = settings.maxFramesPerSecond, onSelect = { onSettingsChange(settings.copy(maxFramesPerSecond = it)) }, label = { "$it fps" }, enabled = !streaming)
        }
        status?.let { JwBanner(text = it, tone = JwTone.Neutral) }
        stats?.let { JwText(text = it.describe(), style = JwTheme.textStyles.code, modifier = Modifier.padding(horizontal = JwSpacing.medium)) }
        Box(Modifier.weight(1f).fillMaxSize().padding(JwSpacing.medium), contentAlignment = Alignment.Center) {
            if (frame == null) {
                JwEmptyState(title = "No frame yet", description = "Start the stream to see the app's windows as the app itself draws them.")
            } else {
                Image(bitmap = frame, contentDescription = "App screen", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private fun StreamStatsSnapshot.describe(): String = String.format(
    Locale.ROOT,
    "%.1f fps · latency %.0f ms (p95 %d) · %.1f KB/frame · %.0f KB/s · app: main %.2f ms, copy %.1f, compose %.1f, jpeg %.1f, base64 %.1f ms · transport %.1f ms · host decode %.1f ms",
    framesPerSecond,
    averageLatencyMillis,
    p95LatencyMillis,
    averageFrameBytes / 1024,
    kilobytesPerSecond,
    averageMainThreadMicros / 1000,
    averagePixelCopyMillis,
    averageComposeMillis,
    averageCompressMillis,
    averageBase64Millis,
    averageTransportMillis,
    averageDecodeMillis,
)
