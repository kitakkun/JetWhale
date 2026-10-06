package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.LongRun
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class TrackedDispatcherTest {
    /** Holds dispatched tasks until the test runs them, so queueing is observable. */
    private class ManualDispatcher : CoroutineDispatcher() {
        val pending = mutableListOf<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            pending += block
        }

        fun runAll() {
            val tasks = pending.toList()
            pending.clear()
            tasks.forEach(Runnable::run)
        }
    }

    @Test
    fun `a dispatched task counts as queued until it runs and as completed after`() {
        val delegate = ManualDispatcher()
        val recorder = DispatcherRecorder(name = "Main", longRunThreshold = 1.hours)
        val tracked = TrackedDispatcher(delegate, recorder)

        tracked.dispatch(EmptyCoroutineContext, Runnable {})
        tracked.dispatch(EmptyCoroutineContext, Runnable {})
        assertEquals(2, recorder.snapshot().queued)

        delegate.runAll()

        val stats = recorder.snapshot()
        assertEquals(0, stats.queued)
        assertEquals(0, stats.running)
        assertEquals(2, stats.completedTasks)
        assertEquals(emptyList(), stats.longRuns)
    }

    @Test
    fun `every task is dispatched so that none runs in place untimed`() {
        val tracked = TrackedDispatcher(ManualDispatcher(), DispatcherRecorder(name = "Main", longRunThreshold = 1.hours))

        assertTrue(tracked.isDispatchNeeded(EmptyCoroutineContext))
    }

    @Test
    fun `a dispatcher name can be tracked only once`() {
        val inspector = JetWhaleCoroutineInspectorAgentPlugin()
        inspector.track(ManualDispatcher(), name = "IO", longRunThreshold = 1.hours)

        assertFailsWith<IllegalArgumentException> { inspector.track(ManualDispatcher(), name = "IO", longRunThreshold = 1.hours) }
    }

    @Test
    fun `the unconfined dispatcher cannot be tracked`() {
        assertFailsWith<IllegalArgumentException> {
            JetWhaleCoroutineInspectorAgentPlugin().track(Dispatchers.Unconfined, name = "Unconfined", longRunThreshold = 1.hours)
        }
    }

    @Test
    fun `a task the delegate refuses is not left counted as queued`() {
        val refusing = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable): Unit = throw IllegalStateException("closed")
        }
        val recorder = DispatcherRecorder(name = "Closed", longRunThreshold = 1.hours)

        assertFailsWith<IllegalStateException> { TrackedDispatcher(refusing, recorder).dispatch(EmptyCoroutineContext, Runnable {}) }

        assertEquals(0, recorder.snapshot().queued)
    }

    @Test
    fun `a task counts as running while it runs`() {
        val delegate = ManualDispatcher()
        val recorder = DispatcherRecorder(name = "Main", longRunThreshold = 1.hours)
        var runningInside = -1
        TrackedDispatcher(delegate, recorder).dispatch(EmptyCoroutineContext, Runnable { runningInside = recorder.snapshot().running })

        delegate.runAll()

        assertEquals(1, runningInside)
    }

    @Test
    fun `a task at or over the threshold is listed as a long run with its coroutine name`() {
        val delegate = ManualDispatcher()
        val recorder = DispatcherRecorder(name = "Main", longRunThreshold = Duration.ZERO)

        TrackedDispatcher(delegate, recorder).dispatch(CoroutineName("blocker"), Runnable {})
        delegate.runAll()

        assertEquals(listOf("blocker"), recorder.snapshot().longRuns.map(LongRun::coroutineName))
        assertEquals(1, recorder.clearLongRuns())
        assertEquals(emptyList(), recorder.snapshot().longRuns)
    }
}
