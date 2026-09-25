package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.FlowValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FlowRecorderTest {
    @Test
    fun `a completed collection records its values newest first`() = runTest {
        val recorder = FlowRecorder("numbers")

        assertEquals(listOf(1, 2, 3), trackedFlow(flowOf(1, 2, 3), recorder).toList())

        val info = recorder.snapshot()
        assertEquals(1, info.collections)
        assertEquals(1, info.completions)
        assertEquals(3, info.emissions)
        assertEquals(listOf("3", "2", "1"), info.recentValues.map(FlowValue::text))
        assertEquals(0, info.activeCollectors)
    }

    @Test
    fun `a collection that stops early counts as cancelled`() = runTest {
        val recorder = FlowRecorder("numbers")

        trackedFlow(flowOf(1, 2, 3), recorder).first()

        assertEquals(1, recorder.snapshot().cancellations)
        assertEquals(0, recorder.snapshot().activeCollectors)
    }

    @Test
    fun `a failing upstream counts as failed and still throws`() = runTest {
        val recorder = FlowRecorder("broken")

        assertFailsWith<IllegalStateException> { trackedFlow(flow<Int> { error("boom") }, recorder).toList() }

        assertEquals(1, recorder.snapshot().failures)
    }

    @Test
    fun `collections running at the same time are counted while they run`() = runTest {
        val recorder = FlowRecorder("events")
        val upstream = MutableSharedFlow<Int>()
        val collectors = List(2) { launch { trackedFlow(upstream, recorder).collect {} } }
        runCurrent()

        assertEquals(2, recorder.snapshot().activeCollectors)

        collectors.forEach { it.cancel() }
        runCurrent()
        assertEquals(0, recorder.snapshot().activeCollectors)
    }

    @Test
    fun `a fast flow reports its full rate`() = runTest {
        val recorder = FlowRecorder("fast")

        trackedFlow((1..3_000).asFlow(), recorder).toList()

        // All 3,000 land in the last ten seconds, so the rate is at least 300 per second.
        assertTrue(recorder.snapshot().emissionsPerSecond >= 300.0, recorder.snapshot().emissionsPerSecond.toString())
    }

    @Test
    fun `a long value is cut to a bounded length`() = runTest {
        val recorder = FlowRecorder("text")

        trackedFlow(flowOf("x".repeat(1_000)), recorder).toList()

        assertEquals(200, recorder.snapshot().recentValues.single().text.length)
    }

    @Test
    fun `an emission committed out of order joins its own second instead of adding a bucket`() {
        val buckets = emptyList<RateBucket>()
            .countingEmissionAt(100)
            .countingEmissionAt(101)
            .countingEmissionAt(100)
            .countingEmissionAt(101)

        assertEquals(listOf(RateBucket(100, 2), RateBucket(101, 2)), buckets)
    }
}
