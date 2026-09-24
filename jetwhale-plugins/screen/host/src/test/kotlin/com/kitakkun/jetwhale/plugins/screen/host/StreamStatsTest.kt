package com.kitakkun.jetwhale.plugins.screen.host

import com.kitakkun.jetwhale.plugins.screen.protocol.FrameTiming
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamStatsTest {
    @Test
    fun `latency runs from the capture on the app to the frame decoded on the host`() {
        val stats = StreamStats()
        stats.record(frame(capturedAt = 1_000, displayedAt = 1_040, bytes = 1_000))
        stats.record(frame(capturedAt = 1_100, displayedAt = 1_160, bytes = 1_000))

        assertEquals(50.0, stats.snapshot().averageLatencyMillis)
    }

    @Test
    fun `only the last five seconds count`() {
        val stats = StreamStats()
        stats.record(frame(capturedAt = 0, displayedAt = 10, bytes = 1_000))
        repeat(10) { stats.record(frame(capturedAt = 10_000L + it * 100, displayedAt = 10_010L + it * 100, bytes = 1_000)) }

        val snapshot = stats.snapshot()
        assertEquals(10, snapshot.frames)
        assertEquals(2.0, snapshot.framesPerSecond)
    }

    @Test
    fun `bandwidth is the bytes of the window spread over it`() {
        val stats = StreamStats()
        repeat(5) { stats.record(frame(capturedAt = it * 100L, displayedAt = it * 100L + 10, bytes = 10_240)) }

        assertEquals(10.0, stats.snapshot().kilobytesPerSecond)
    }

    @Test
    fun `an agent clock ahead of the host is measured from the midpoint of the round trip`() {
        assertEquals(4_800L, agentClockOffsetMillis(sentEpochMillis = 10_000, agentEpochMillis = 14_820, receivedEpochMillis = 10_040))
    }

    private fun frame(capturedAt: Long, displayedAt: Long, bytes: Int) = ReceivedFrame(
        jpegBytes = bytes,
        timing = FrameTiming(captureStartedEpochMillis = capturedAt, mainThreadMicros = 100, pixelCopyMillis = 5, composeMillis = 1, compressMillis = 8, base64Millis = 1, sentEpochMillis = capturedAt + 15),
        receivedEpochMillis = displayedAt - 2,
        displayedEpochMillis = displayedAt,
    )
}
