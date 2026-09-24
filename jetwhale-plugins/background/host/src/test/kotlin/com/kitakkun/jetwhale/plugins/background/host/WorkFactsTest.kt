package com.kitakkun.jetwhale.plugins.background.host

import com.kitakkun.jetwhale.plugins.background.protocol.WorkState
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkFactsTest {
    @Test
    fun `periodic work shows its interval and flex`() {
        val item = workItem("WorkManager", "sync", WorkState.Enqueued, tags = emptyList(), canRunNow = true).copy(periodMillis = 900_000, flexMillis = 300_000)

        assertEquals("15m (flex 5m)", workFactsOf(item).single { it.label == "Repeats every" }.value)
    }

    @Test
    fun `facts the source does not report are left out`() {
        val item = workItem("JobScheduler", "cleanup", WorkState.Scheduled, tags = emptyList(), canRunNow = false).copy(runAttemptCount = null)

        assertEquals(listOf("Class", "Id"), workFactsOf(item).map(WorkFact::label))
    }

    @Test
    fun `a class name loses its package in the list`() {
        assertEquals("SyncWorker", shortName("com.example.app.sync.SyncWorker"))
    }
}
