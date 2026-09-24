package com.kitakkun.jetwhale.plugins.screen.host

import com.kitakkun.jetwhale.plugins.screen.protocol.FrameTiming

/** The span the rates and averages cover: long enough to smooth, short enough to follow a change. */
private const val WINDOW_MILLIS = 5_000L

/**
 * One frame as the host saw it arrive.
 *
 * @property receivedEpochMillis When the message handler got the frame, after the messenger parsed it.
 * @property displayedEpochMillis When the frame was decoded and ready to draw, on the host's clock.
 */
internal class ReceivedFrame(
    val jpegBytes: Int,
    val timing: FrameTiming,
    val receivedEpochMillis: Long,
    val displayedEpochMillis: Long,
)

/**
 * What streaming costs and delivers over the last [WINDOW_MILLIS].
 *
 * Latency runs from the agent starting the capture to the host having the frame decoded, with the
 * agent's times moved onto the host's clock by [agentClockOffsetMillis].
 */
internal data class StreamStatsSnapshot(
    val frames: Int,
    val framesPerSecond: Double,
    val averageLatencyMillis: Double,
    val p95LatencyMillis: Long,
    val averageFrameBytes: Double,
    val kilobytesPerSecond: Double,
    val averageMainThreadMicros: Double,
    val averagePixelCopyMillis: Double,
    val averageComposeMillis: Double,
    val averageCompressMillis: Double,
    val averageBase64Millis: Double,
    val averageTransportMillis: Double,
    val averageDecodeMillis: Double,
)

/** Keeps the frames of the last few seconds and summarizes them. */
internal class StreamStats {
    private val frames = ArrayDeque<ReceivedFrame>()

    fun record(frame: ReceivedFrame) {
        frames += frame
        while (frames.first().displayedEpochMillis < frame.displayedEpochMillis - WINDOW_MILLIS) frames.removeFirst()
    }

    fun clear() = frames.clear()

    fun snapshot(): StreamStatsSnapshot {
        if (frames.isEmpty()) {
            return StreamStatsSnapshot(
                frames = 0,
                framesPerSecond = 0.0,
                averageLatencyMillis = 0.0,
                p95LatencyMillis = 0,
                averageFrameBytes = 0.0,
                kilobytesPerSecond = 0.0,
                averageMainThreadMicros = 0.0,
                averagePixelCopyMillis = 0.0,
                averageComposeMillis = 0.0,
                averageCompressMillis = 0.0,
                averageBase64Millis = 0.0,
                averageTransportMillis = 0.0,
                averageDecodeMillis = 0.0,
            )
        }
        val latencies = frames.map { it.displayedEpochMillis - it.timing.captureStartedEpochMillis }.sorted()
        val spanSeconds = WINDOW_MILLIS / 1000.0
        return StreamStatsSnapshot(
            frames = frames.size,
            framesPerSecond = frames.size / spanSeconds,
            averageLatencyMillis = latencies.average(),
            p95LatencyMillis = latencies[((latencies.size - 1) * 0.95).toInt()],
            averageFrameBytes = frames.map(ReceivedFrame::jpegBytes).average(),
            kilobytesPerSecond = frames.sumOf(ReceivedFrame::jpegBytes) / 1024.0 / spanSeconds,
            averageMainThreadMicros = frames.map { it.timing.mainThreadMicros }.average(),
            averagePixelCopyMillis = frames.map { it.timing.pixelCopyMillis }.average(),
            averageComposeMillis = frames.map { it.timing.composeMillis }.average(),
            averageCompressMillis = frames.map { it.timing.compressMillis }.average(),
            averageBase64Millis = frames.map { it.timing.base64Millis }.average(),
            averageTransportMillis = frames.map { it.receivedEpochMillis - it.timing.sentEpochMillis }.average(),
            averageDecodeMillis = frames.map { it.displayedEpochMillis - it.receivedEpochMillis }.average(),
        )
    }
}

/**
 * How far the agent's clock runs ahead of the host's, from one request: the agent read its clock
 * at [agentEpochMillis] somewhere between the host sending at [sentEpochMillis] and receiving at
 * [receivedEpochMillis], so the midpoint is the best guess, off by at most half the round trip.
 */
internal fun agentClockOffsetMillis(sentEpochMillis: Long, agentEpochMillis: Long, receivedEpochMillis: Long): Long = agentEpochMillis - (sentEpochMillis + receivedEpochMillis) / 2
