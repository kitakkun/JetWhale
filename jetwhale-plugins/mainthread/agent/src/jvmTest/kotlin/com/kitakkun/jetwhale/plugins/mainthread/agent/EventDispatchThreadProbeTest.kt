package com.kitakkun.jetwhale.plugins.mainthread.agent

import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorSettings
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EventDispatchThreadProbeTest {
    @Test
    fun `a slow event on the dispatch thread is recorded with samples and the queue is put back`() {
        // Without a display there is no dispatch thread to watch; the probe says so instead.
        if (GraphicsEnvironment.isHeadless()) return
        val recorder = MainThreadRecorder(
            clock = SystemMonitorClock(),
            initialSettings = MonitorSettings(longTaskThresholdMillis = 50, sampleIntervalMillis = 10, unresponsiveThresholdMillis = 10_000),
        )
        val probe = createMainThreadProbe(recorder) { "label" }
        EventQueue.invokeAndWait { }
        val before = Toolkit.getDefaultToolkit().systemEventQueue

        probe.start()
        EventQueue.invokeAndWait { Thread.sleep(200) }
        probe.stop()
        EventQueue.invokeAndWait { }

        assertSame(before, Toolkit.getDefaultToolkit().systemEventQueue)
        val report = recorder.report(probe.capabilities)
        val task = report.longTasks.single { it.durationMillis >= 200 }
        assertTrue(task.label.startsWith("InvocationEvent"), task.label)
        assertTrue(task.sampleCount > 0)
        assertEquals(true, report.hotspots.any { hotspot -> hotspot.frames.any { "EventDispatchThreadProbeTest" in it } })
    }
}
