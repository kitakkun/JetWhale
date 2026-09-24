package com.kitakkun.jetwhale.plugins.coroutines.host

import com.kitakkun.jetwhale.plugins.coroutines.protocol.ClearedLongRuns
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineTree
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStatsReport
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowReport
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CoroutineInspectorStateTest {
    private class FlakyClient : CoroutineInspectorClient {
        var reachable = false
        val calls = mutableListOf<String>()

        override suspend fun coroutineTree(): CoroutineTree {
            calls += "tree"
            if (!reachable) throw JetWhaleMessagingException("offline")
            return CoroutineTree(roots = emptyList(), coroutineCount = 0, truncated = false, capturedAtEpochMillis = 0)
        }

        override suspend fun dispatcherStats(): DispatcherStatsReport {
            calls += "dispatchers"
            return DispatcherStatsReport(dispatchers = emptyList(), capturedAtEpochMillis = 0)
        }

        override suspend fun trackedFlows(): TrackedFlowReport {
            calls += "flows"
            return TrackedFlowReport(flows = emptyList(), capturedAtEpochMillis = 0)
        }

        override suspend fun dump(): CoroutineDump {
            calls += "dump"
            return CoroutineDump(text = "dump", unavailableReason = null)
        }

        override suspend fun clearLongRuns(): ClearedLongRuns = ClearedLongRuns(cleared = 3)
    }

    private val client = FlakyClient()

    // The fake answers without suspending, so every launched read has finished when refresh returns.
    private val state = CoroutineInspectorState(client, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `refreshing a tab reads only what that tab shows`() {
        state.refresh(InspectorTab.Flows)

        assertEquals(listOf("flows"), client.calls)
        assertNotNull(state.flows)
        assertNull(state.tree)
    }

    @Test
    fun `a failed read is reported and a later successful one clears the report`() {
        state.refresh(InspectorTab.Coroutines)
        assertEquals(true, state.status?.isError)

        client.reachable = true
        state.refresh(InspectorTab.Coroutines)

        assertNull(state.status)
        assertNotNull(state.tree)
    }

    @Test
    fun `collapsing twice expands again`() {
        state.toggleCollapsed("c1")
        state.toggleCollapsed("c1")

        assertEquals(emptySet(), state.collapsed)
    }
}
