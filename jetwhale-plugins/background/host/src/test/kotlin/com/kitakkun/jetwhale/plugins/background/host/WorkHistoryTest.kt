package com.kitakkun.jetwhale.plugins.background.host

import com.kitakkun.jetwhale.plugins.background.protocol.WorkState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkHistoryTest {
    private val sync = workItem("WorkManager", "sync", WorkState.Enqueued, tags = emptyList(), canRunNow = true)

    @Test
    fun `a state is recorded when the work is first seen and again only when it changes`() {
        var history = recordTransitions(emptyMap(), listOf(sync), nowEpochMillis = 1)
        history = recordTransitions(history, listOf(sync), nowEpochMillis = 2)
        history = recordTransitions(history, listOf(sync.copy(state = WorkState.Running)), nowEpochMillis = 3)

        assertEquals(listOf(StateTransition(WorkState.Enqueued, 1), StateTransition(WorkState.Running, 3)), history.getValue(sync.key))
    }

    @Test
    fun `work gone from a snapshot keeps its history`() {
        val history = recordTransitions(recordTransitions(emptyMap(), listOf(sync), nowEpochMillis = 1), emptyList(), nowEpochMillis = 2)

        assertEquals(listOf(StateTransition(WorkState.Enqueued, 1)), history.getValue(sync.key))
    }

    @Test
    fun `the same id in two sources is two histories`() {
        val job = sync.copy(source = "JobScheduler", state = WorkState.Scheduled)

        val history = recordTransitions(emptyMap(), listOf(sync, job), nowEpochMillis = 1)

        assertEquals(2, history.size)
    }

    @Test
    fun `periodic work that cycles forever keeps only the latest transitions`() {
        var history = emptyMap<WorkKey, List<StateTransition>>()
        repeat(MAX_TRANSITIONS_PER_WORK) { cycle ->
            history = recordTransitions(history, listOf(sync.copy(state = WorkState.Running)), nowEpochMillis = cycle * 2L)
            history = recordTransitions(history, listOf(sync), nowEpochMillis = cycle * 2L + 1)
        }

        val kept = history.getValue(sync.key)
        assertEquals(MAX_TRANSITIONS_PER_WORK, kept.size)
        assertEquals(MAX_TRANSITIONS_PER_WORK * 2L - 1, kept.last().atEpochMillis)
    }

    @Test
    fun `an app that keeps creating one-off work keeps only the most recent departed histories`() {
        var history = emptyMap<WorkKey, List<StateTransition>>()
        repeat(MAX_DEPARTED_WORK + 10) { n ->
            history = recordTransitions(history, listOf(sync.copy(id = "once-$n")), nowEpochMillis = n.toLong())
        }
        history = recordTransitions(history, listOf(sync), nowEpochMillis = MAX_DEPARTED_WORK + 10L)

        assertEquals(MAX_DEPARTED_WORK + 1, history.size)
        assertFalse(WorkKey("WorkManager", "once-9") in history)
        assertTrue(WorkKey("WorkManager", "once-10") in history)
        assertTrue(sync.key in history)
    }
}
