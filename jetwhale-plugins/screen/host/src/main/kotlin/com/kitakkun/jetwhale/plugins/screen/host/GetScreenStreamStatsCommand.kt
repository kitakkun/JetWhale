package com.kitakkun.jetwhale.plugins.screen.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetScreenStreamStatsCommand(
    private val controller: ScreenStreamController,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getStats"
    override val description =
        "Reports the stream over the last five seconds: frames per second, capture-to-display latency (average and p95), bytes per frame and bandwidth, and where each frame's time went: on the app (main thread, pixel copy, compositing, JPEG compression, Base64), in transport (serialization, socket, parsing) and on the host (decoding)."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val stats = controller.stats()
        return buildJsonObject {
            put("frames", stats.frames)
            put("framesPerSecond", stats.framesPerSecond)
            put("averageLatencyMillis", stats.averageLatencyMillis)
            put("p95LatencyMillis", stats.p95LatencyMillis)
            put("averageFrameBytes", stats.averageFrameBytes)
            put("kilobytesPerSecond", stats.kilobytesPerSecond)
            put("averageMainThreadMicros", stats.averageMainThreadMicros)
            put("averagePixelCopyMillis", stats.averagePixelCopyMillis)
            put("averageComposeMillis", stats.averageComposeMillis)
            put("averageCompressMillis", stats.averageCompressMillis)
            put("averageBase64Millis", stats.averageBase64Millis)
            put("averageTransportMillis", stats.averageTransportMillis)
            put("averageDecodeMillis", stats.averageDecodeMillis)
        }.toString()
    }
}
