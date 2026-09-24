package com.kitakkun.jetwhale.plugins.screen.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.plugins.screen.protocol.GrantFrameCredit
import com.kitakkun.jetwhale.plugins.screen.protocol.ScreenFrame
import com.kitakkun.jetwhale.plugins.screen.protocol.StartScreenStream
import com.kitakkun.jetwhale.plugins.screen.protocol.StopScreenStream
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import com.kitakkun.jetwhale.protocol.messaging.request
import com.kitakkun.jetwhale.protocol.messaging.trySend
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import org.jetbrains.skia.Image as SkiaImage

/**
 * Two frames in flight: one being drawn while the next is captured and sent, which hides the round
 * trip without letting a slow link queue up stale pictures.
 */
private const val FRAMES_IN_FLIGHT = 2

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class ScreenHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = ScreenHostPlugin()
}

@OptIn(ExperimentalJetWhaleApi::class)
private class ScreenHostPlugin :
    JetWhaleMessagingHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin,
    ScreenStreamController {

    private var latestFrame: ImageBitmap? by mutableStateOf(null)
    private var statsSnapshot: StreamStatsSnapshot? by mutableStateOf(null)
    private var status: String? by mutableStateOf(null)
    private var streaming: Boolean by mutableStateOf(false)

    // Half the screen's size at JPEG quality 70 reads well and stays well under a megabyte a second.
    private var settings: StreamSettings by mutableStateOf(StreamSettings(scale = 0.5f, jpegQuality = 70, maxFramesPerSecond = 30))
    private val stats = StreamStats()

    @Volatile
    private var agentClockOffsetMillis = 0L

    override fun JetWhaleMessageHandlers.configure() {
        onEvent<ScreenFrame>(::show)
    }

    private suspend fun show(frame: ScreenFrame) {
        // Taken before decoding, so the stats can tell the transport from the host's own work.
        val receivedEpochMillis = System.currentTimeMillis()
        val jpeg = Base64.decode(frame.jpegBase64)
        latestFrame = SkiaImage.makeFromEncoded(jpeg).toComposeImageBitmap()
        synchronized(stats) {
            val timing = frame.timing.copy(
                captureStartedEpochMillis = frame.timing.captureStartedEpochMillis - agentClockOffsetMillis,
                sentEpochMillis = frame.timing.sentEpochMillis - agentClockOffsetMillis,
            )
            stats.record(ReceivedFrame(jpegBytes = jpeg.size, timing = timing, receivedEpochMillis = receivedEpochMillis, displayedEpochMillis = System.currentTimeMillis()))
            statsSnapshot = stats.snapshot()
        }
        // The credit goes back once the frame is decoded, so a host that cannot keep up slows the
        // agent down instead of letting frames pile up in the socket.
        messenger.trySend(GrantFrameCredit(count = 1))
    }

    override suspend fun start(scale: Float, jpegQuality: Int, maxFramesPerSecond: Int): String {
        settings = StreamSettings(scale = scale, jpegQuality = jpegQuality, maxFramesPerSecond = maxFramesPerSecond)
        synchronized(stats, stats::clear)
        val sentEpochMillis = System.currentTimeMillis()
        val reply = try {
            messenger.request(StartScreenStream(scale = scale, jpegQuality = jpegQuality, maxFramesPerSecond = maxFramesPerSecond, initialCredits = FRAMES_IN_FLIGHT))
        } catch (e: JetWhaleMessagingException) {
            return "Failed to reach the app: ${e.message}".also { status = it }
        }
        agentClockOffsetMillis = agentClockOffsetMillis(sentEpochMillis, reply.agentEpochMillis, receivedEpochMillis = System.currentTimeMillis())
        streaming = reply.streaming
        return (reply.unsupportedReason ?: "Streaming at ${(scale * 100).toInt()}%, quality $jpegQuality, up to $maxFramesPerSecond fps.").also { status = it }
    }

    override suspend fun stop(): String {
        try {
            messenger.request(StopScreenStream)
        } catch (e: JetWhaleMessagingException) {
            return "Failed to reach the app: ${e.message}".also { status = it }
        }
        streaming = false
        return "Stopped.".also { status = it }
    }

    override fun stats(): StreamStatsSnapshot = synchronized(stats, stats::snapshot)

    @Composable
    override fun Content() {
        ScreenStreamScreen(
            frame = latestFrame,
            stats = statsSnapshot,
            status = status,
            streaming = streaming,
            settings = settings,
            onSettingsChange = { settings = it },
            onStart = { pluginScope.launch { start(settings.scale, settings.jpegQuality, settings.maxFramesPerSecond) } },
            onStop = { pluginScope.launch { stop() } },
        )
    }

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        StartScreenStreamCommand(this),
        StopScreenStreamCommand(this),
        GetScreenStreamStatsCommand(this),
    )
}
