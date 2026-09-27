package com.kitakkun.jetwhale.plugins.coroutines.host

import com.kitakkun.jetwhale.plugins.coroutines.protocol.ClearedLongRuns
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDetail
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineNode
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineState
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineTree
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStatsReport
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowReport
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CompletableDeferred
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
            return CoroutineTree(roots = treeRoots, coroutineCount = 0, truncated = false, capturedAtEpochMillis = 0)
        }

        override suspend fun dispatcherStats(): DispatcherStatsReport {
            calls += "dispatchers"
            return DispatcherStatsReport(dispatchers = emptyList(), untracked = emptyList(), capturedAtEpochMillis = 0)
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

        var treeRoots = listOf(node("c1", "Application", CoroutineState.Active, 0, node("c2", "sync", CoroutineState.Active, 0)))

        override suspend fun coroutineDetail(id: String): CoroutineDetail {
            calls += "detail:$id"
            return CoroutineDetail(id = id, found = true, debugState = null, suspensionStack = emptyList(), creationStack = emptyList(), stackUnavailableReason = "no probes")
        }
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
    fun `a refresh while the same tab is still being read starts no second read`() {
        val gate = CompletableDeferred<Unit>()
        val slow = object : CoroutineInspectorClient by client {
            override suspend fun trackedFlows(): TrackedFlowReport {
                gate.await()
                return client.trackedFlows()
            }
        }
        val slowState = CoroutineInspectorState(slow, CoroutineScope(Dispatchers.Unconfined))

        slowState.refresh(InspectorTab.Flows)
        slowState.refresh(InspectorTab.Flows)
        gate.complete(Unit)

        assertEquals(listOf("flows"), client.calls)
    }

    @Test
    fun `a dispatcher read started before clearing long runs does not undo the clear`() {
        val gate = CompletableDeferred<Unit>()
        var reads = 0
        val slowFirstRead = object : CoroutineInspectorClient by client {
            override suspend fun dispatcherStats(): DispatcherStatsReport {
                val read = ++reads
                if (read == 1) gate.await()
                return DispatcherStatsReport(dispatchers = emptyList(), untracked = emptyList(), capturedAtEpochMillis = read.toLong())
            }
        }
        val clearingState = CoroutineInspectorState(slowFirstRead, CoroutineScope(Dispatchers.Unconfined))

        clearingState.refresh(InspectorTab.Dispatchers)
        clearingState.clearLongRuns()
        gate.complete(Unit)

        assertEquals(2, clearingState.dispatchers?.capturedAtEpochMillis)
    }

    @Test
    fun `selecting a coroutine reads its detail and keeps where it lives`() {
        client.reachable = true
        state.refresh(InspectorTab.Coroutines)

        state.select("c2")

        assertEquals("c2", state.detail?.id)
        assertEquals(listOf("c1"), state.lastSeenSelection?.ancestors?.map(CoroutineNode::id))
    }

    @Test
    fun `a selected coroutine that disappears from the tree stays selected with how it last looked`() {
        client.reachable = true
        state.refresh(InspectorTab.Coroutines)
        state.select("c2")

        client.treeRoots = listOf(node("c1", "Application", CoroutineState.Active, 0))
        state.refresh(InspectorTab.Coroutines)

        assertEquals("c2", state.selectedId)
        assertEquals("sync", state.lastSeenSelection?.node?.name)
    }

    @Test
    fun `a detail that arrives after another coroutine was selected is dropped`() {
        val gate = CompletableDeferred<Unit>()
        val slowDetail = object : CoroutineInspectorClient by client {
            override suspend fun coroutineDetail(id: String): CoroutineDetail {
                if (id == "c1") gate.await()
                return client.coroutineDetail(id)
            }
        }
        val selectingState = CoroutineInspectorState(slowDetail, CoroutineScope(Dispatchers.Unconfined))

        selectingState.select("c1")
        selectingState.select("c2")
        gate.complete(Unit)

        assertEquals("c2", selectingState.detail?.id)
    }

    @Test
    fun `clearing the selection forgets the detail`() {
        state.select("c2")

        state.select(null)

        assertNull(state.detail)
        assertNull(state.lastSeenSelection)
    }

    @Test
    fun `collapsing twice expands again`() {
        state.toggleCollapsed("c1")
        state.toggleCollapsed("c1")

        assertEquals(emptySet(), state.collapsed)
    }
}
