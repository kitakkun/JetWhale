package com.kitakkun.jetwhale.plugins.screen.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FramePacerTest {
    @Test
    fun `the first frame is captured right away`() {
        assertEquals(0L, FramePacer(minIntervalMillis = 100, initialCredits = 2).delayBeforeCapture(nowMillis = 1_000))
    }

    @Test
    fun `nothing is captured until the app redraws`() {
        val pacer = FramePacer(minIntervalMillis = 100, initialCredits = 2)
        pacer.onCaptureStarted(nowMillis = 1_000)

        assertNull(pacer.delayBeforeCapture(nowMillis = 2_000))
        pacer.markDirty()
        assertEquals(0L, pacer.delayBeforeCapture(nowMillis = 2_000))
    }

    @Test
    fun `a redraw soon after a capture waits out the interval`() {
        val pacer = FramePacer(minIntervalMillis = 100, initialCredits = 2)
        pacer.onCaptureStarted(nowMillis = 1_000)
        pacer.markDirty()

        assertEquals(70L, pacer.delayBeforeCapture(nowMillis = 1_030))
    }

    @Test
    fun `without credit a redraw waits for the host`() {
        val pacer = FramePacer(minIntervalMillis = 100, initialCredits = 1)
        pacer.onCaptureStarted(nowMillis = 1_000)
        pacer.markDirty()

        assertNull(pacer.delayBeforeCapture(nowMillis = 5_000))
        pacer.grant(1)
        assertEquals(0L, pacer.delayBeforeCapture(nowMillis = 5_000))
    }

    @Test
    fun `many redraws while waiting become one capture`() {
        val pacer = FramePacer(minIntervalMillis = 100, initialCredits = 5)
        pacer.onCaptureStarted(nowMillis = 1_000)
        repeat(10) { pacer.markDirty() }
        pacer.onCaptureStarted(nowMillis = 1_100)

        assertNull(pacer.delayBeforeCapture(nowMillis = 1_300))
        assertEquals(3, pacer.remainingCredits)
    }
}
