package com.kitakkun.jetwhale.plugins.mainthread.host

import com.kitakkun.jetwhale.plugins.mainthread.protocol.MainThreadReport
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorSettings
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ViolationKind
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainThreadMonitorTest {
    private val client = FakeMainThreadClient(report(hotspots = emptyList(), violations = listOf(violation(ViolationKind.DiskRead, "x")), longTasks = emptyList()))

    // Unconfined runs a launched call in place and the fake never suspends, so each call has
    // finished when the action returns.
    private val monitor = MainThreadMonitor(client, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `clearing empties the shown report`() {
        runBlocking { monitor.load() }

        monitor.resetStats()

        assertTrue(monitor.report?.violations.orEmpty().isEmpty())
        assertEquals(false, monitor.status?.isError)
    }

    @Test
    fun `new thresholds reach the app and the shown report`() {
        val settings = MonitorSettings(longTaskThresholdMillis = 250, sampleIntervalMillis = 10, unresponsiveThresholdMillis = 3_000)

        monitor.updateSettings(settings)

        assertEquals(settings, monitor.report?.settings)
    }

    @Test
    fun `a report arriving clears an earlier connection error`() {
        var failing = true
        val flakyClient = object : MainThreadClient by client {
            override suspend fun requestReport(): MainThreadReport = if (failing) throw JetWhaleMessagingException("offline") else client.requestReport()
        }
        val flakyMonitor = MainThreadMonitor(flakyClient, CoroutineScope(Dispatchers.Unconfined))

        flakyMonitor.refresh()
        assertEquals(true, flakyMonitor.status?.isError)
        failing = false
        flakyMonitor.refresh()

        assertNull(flakyMonitor.status)
    }

    @Test
    fun `the thresholds form accepts only positive whole numbers`() {
        assertEquals(MonitorSettings(longTaskThresholdMillis = 100, sampleIntervalMillis = 20, unresponsiveThresholdMillis = 5_000), validSettingsOf(longTaskThresholdText = "100", sampleIntervalText = "20", unresponsiveThresholdText = "5000"))
        assertNull(validSettingsOf(longTaskThresholdText = "0", sampleIntervalText = "20", unresponsiveThresholdText = "5000"))
        assertNull(validSettingsOf(longTaskThresholdText = "100", sampleIntervalText = "fast", unresponsiveThresholdText = "5000"))
    }
}
