package com.kitakkun.jetwhale.plugins.mainthread.agent

import com.kitakkun.jetwhale.plugins.mainthread.protocol.Hotspot
import com.kitakkun.jetwhale.plugins.mainthread.protocol.LongTask
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorCapabilities
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorSettings
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ViolationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainThreadRecorderTest {
    private val clock = FakeClock()
    private val recorder = MainThreadRecorder(
        clock = clock,
        initialSettings = MonitorSettings(longTaskThresholdMillis = 100, sampleIntervalMillis = 20, unresponsiveThresholdMillis = 1_000),
    )
    private val capabilities = MonitorCapabilities(platform = "test", taskTiming = true, stackSampling = true, strictMode = true, frameTiming = true, note = null)

    private val appStack = listOf("android.os.Something.x(A.java:1)", "com.example.Slow.work(Slow.kt:5)", "android.os.Looper.loop(Looper.java:2)")

    @Test
    fun `a task under the threshold is not recorded`() {
        recorder.recordTaskStart("short", extraLabel = null)
        clock.advance(99)
        recorder.recordTaskEnd()

        assertTrue(recorder.buildReport(capabilities).longTasks.isEmpty())
    }

    @Test
    fun `a long task is recorded with its formatted label and the app's own label`() {
        recorder.recordTaskStart(">>>>> Dispatching to Handler (android.os.Handler) {1a} com.example.Job@2b: 0", extraLabel = "coroutine #3")
        clock.advance(150)
        recorder.recordTaskEnd()

        val task = recorder.buildReport(capabilities).longTasks.single()
        assertEquals("Handler (android.os.Handler) com.example.Job · coroutine #3", task.label)
        assertEquals(150, task.durationMillis)
    }

    @Test
    fun `a task is sampled only past the threshold and at most once per interval`() {
        var reads = 0
        recorder.recordTaskStart("task", extraLabel = null)

        clock.advance(50)
        recorder.sampleIfDue {
            reads++
            appStack
        }
        clock.advance(60)
        recorder.sampleIfDue {
            reads++
            appStack
        }
        clock.advance(10)
        recorder.sampleIfDue {
            reads++
            appStack
        }
        clock.advance(10)
        recorder.sampleIfDue {
            reads++
            appStack
        }
        recorder.recordTaskEnd()

        assertEquals(2, reads)
        val report = recorder.buildReport(capabilities)
        assertEquals(2, report.longTasks.single().sampleCount)
        val hotspot = report.hotspots.single()
        assertEquals(2, hotspot.sampleCount)
        assertEquals(40, hotspot.blockedMillis)
        assertEquals(1, hotspot.taskCount)
    }

    @Test
    fun `a stack read while the task ended is not attributed to anything`() {
        recorder.recordTaskStart("task", extraLabel = null)
        clock.advance(120)

        recorder.sampleIfDue {
            recorder.recordTaskEnd()
            recorder.recordTaskStart("next", extraLabel = null)
            appStack
        }

        assertTrue(recorder.buildReport(capabilities).hotspots.isEmpty())
    }

    @Test
    fun `samples from different tasks at the same call site add up in one hotspot`() {
        repeat(2) {
            recorder.recordTaskStart("task", extraLabel = null)
            clock.advance(120)
            recorder.sampleIfDue { appStack }
            recorder.recordTaskEnd()
        }

        val hotspot = recorder.buildReport(capabilities).hotspots.single()
        assertEquals(2, hotspot.sampleCount)
        assertEquals(2, hotspot.taskCount)
    }

    @Test
    fun `work outside any task is recorded from when it began`() {
        clock.advance(10)
        recorder.recordStall("outside", busyForMillis = 120)
        clock.advance(30)
        recorder.sampleIfDue { appStack }
        recorder.recordStallEnd()

        val task = recorder.buildReport(capabilities).longTasks.single()
        assertEquals(150, task.durationMillis)
        assertEquals("outside", task.label)
        assertEquals(1, task.sampleCount)
    }

    @Test
    fun `work outside a task is timed from the end of the task before it`() {
        recorder.recordTaskStart("message", extraLabel = null)
        clock.advance(70)
        recorder.recordTaskEnd()
        val messageEndEpoch = clock.epochMillis()
        clock.advance(150)
        recorder.recordStall("outside", busyForMillis = 220)
        recorder.recordStallEnd()

        val task = recorder.buildReport(capabilities).longTasks.single()
        assertEquals(messageEndEpoch, task.startEpochMillis)
        assertEquals(150, task.durationMillis)
    }

    @Test
    fun `a short task followed by short work outside it is not one long task`() {
        recorder.recordTaskStart("message", extraLabel = null)
        clock.advance(70)
        recorder.recordTaskEnd()
        clock.advance(40)
        recorder.recordStall("outside", busyForMillis = 110)
        recorder.recordStallEnd()

        assertTrue(recorder.buildReport(capabilities).longTasks.isEmpty())
    }

    @Test
    fun `the next task ends a stall`() {
        recorder.recordStall("outside", busyForMillis = 200)
        recorder.recordTaskStart("next", extraLabel = null)
        recorder.recordTaskEnd()

        assertEquals(listOf("outside"), recorder.buildReport(capabilities).longTasks.map(LongTask::label))
    }

    @Test
    fun `a stall seen while a task runs is that task`() {
        recorder.recordTaskStart("message", extraLabel = null)
        clock.advance(150)
        recorder.recordStall("outside", busyForMillis = 150)
        recorder.recordStallEnd()
        recorder.recordTaskEnd()

        assertEquals(listOf("message"), recorder.buildReport(capabilities).longTasks.map(LongTask::label))
    }

    @Test
    fun `a task running across a reset is left out of the fresh recording`() {
        recorder.recordTaskStart("before", extraLabel = null)
        clock.advance(120)
        recorder.reset()
        recorder.sampleIfDue { appStack }
        clock.advance(100)
        recorder.recordTaskEnd()

        val report = recorder.buildReport(capabilities)
        assertTrue(report.longTasks.isEmpty() && report.hotspots.isEmpty())
    }

    @Test
    fun `samples keep the interval they were taken at when the interval changes`() {
        recorder.recordTaskStart("task", extraLabel = null)
        clock.advance(120)
        recorder.sampleIfDue { appStack }
        recorder.recordTaskEnd()

        recorder.updateSettings(MonitorSettings(longTaskThresholdMillis = 100, sampleIntervalMillis = 100, unresponsiveThresholdMillis = 1_000))

        assertEquals(20, recorder.buildReport(capabilities).hotspots.single().blockedMillis)
    }

    @Test
    fun `hotspots rank by blocked time rather than sample count`() {
        recorder.recordTaskStart("fine", extraLabel = null)
        clock.advance(120)
        recorder.sampleIfDue { listOf("com.example.Fine.run(F.kt:1)") }
        clock.advance(20)
        recorder.sampleIfDue { listOf("com.example.Fine.run(F.kt:1)") }
        recorder.recordTaskEnd()
        recorder.updateSettings(MonitorSettings(longTaskThresholdMillis = 100, sampleIntervalMillis = 100, unresponsiveThresholdMillis = 1_000))
        recorder.recordTaskStart("coarse", extraLabel = null)
        clock.advance(120)
        recorder.sampleIfDue { listOf("com.example.Coarse.run(C.kt:1)") }
        recorder.recordTaskEnd()

        assertEquals(listOf("com.example.Coarse.run(C.kt:1)", "com.example.Fine.run(F.kt:1)"), recorder.buildReport(capabilities).hotspots.map(Hotspot::signature))
    }

    @Test
    fun `a task past the unresponsive threshold is marked`() {
        recorder.recordTaskStart("stuck", extraLabel = null)
        clock.advance(1_200)
        recorder.recordTaskEnd()

        assertTrue(recorder.buildReport(capabilities).longTasks.single().unresponsive)
    }

    @Test
    fun `the long task buffer keeps only the latest tasks`() {
        repeat(LONG_TASK_CAPACITY + 5) { index ->
            recorder.recordTaskStart("task $index", extraLabel = null)
            clock.advance(100)
            recorder.recordTaskEnd()
        }

        val tasks = recorder.buildReport(capabilities).longTasks
        assertEquals(LONG_TASK_CAPACITY, tasks.size)
        assertEquals("task 5", tasks.first().label)
    }

    @Test
    fun `a full hotspot table still takes in a new call site`() {
        repeat(HOTSPOT_CAPACITY) { index ->
            recorder.recordTaskStart("task", extraLabel = null)
            clock.advance(120)
            recorder.sampleIfDue { listOf("com.example.Site$index.run(S.kt:1)") }
            recorder.sampleIfDue { listOf("com.example.Site$index.run(S.kt:1)") }
            clock.advance(20)
            recorder.sampleIfDue { listOf("com.example.Site$index.run(S.kt:1)") }
            recorder.recordTaskEnd()
        }
        recorder.recordTaskStart("task", extraLabel = null)
        clock.advance(120)
        recorder.sampleIfDue { listOf("com.example.Newcomer.run(N.kt:1)") }
        recorder.recordTaskEnd()

        val signatures = recorder.buildReport(capabilities).hotspots.map(Hotspot::signature)
        assertEquals(HOTSPOT_CAPACITY, signatures.size)
        assertTrue("com.example.Newcomer.run(N.kt:1)" in signatures)
    }

    @Test
    fun `violations group by kind and call site and count up`() {
        recorder.recordViolation(ViolationKind.DiskWrite, "write", appStack)
        recorder.recordViolation(ViolationKind.DiskWrite, "write", appStack)
        recorder.recordViolation(ViolationKind.DiskRead, "read", appStack)

        val groups = recorder.buildReport(capabilities).violations
        assertEquals(listOf(ViolationKind.DiskWrite to 2, ViolationKind.DiskRead to 1), groups.map { it.kind to it.count })
        assertEquals("com.example.Slow.work(Slow.kt:5)", groups.first().callSite)
    }

    @Test
    fun `a violation keeps only the innermost frames of its stack but finds its call site in all of them`() {
        val platformFrames = (1..100).map { "android.os.Frame$it.call(Frame.java:1)" }
        val stack = platformFrames + "com.example.Deep.write(Deep.kt:9)"

        recorder.recordViolation(ViolationKind.DiskWrite, "write", stack)

        val group = recorder.buildReport(capabilities).violations.single()
        assertEquals(platformFrames.take(MAX_STACK_FRAMES), group.stack)
        assertEquals("com.example.Deep.write(Deep.kt:9)", group.callSite)
    }

    @Test
    fun `frames longer than one and a half refresh intervals are janky`() {
        listOf(10.0, 16.0, 24.0, 26.0, 80.0).forEach { recorder.recordFrame(it, refreshIntervalMillis = 16.67) }

        val frames = recorder.buildReport(capabilities).frames
        assertEquals(5, frames.totalFrames)
        assertEquals(2, frames.jankyFrames)
        assertEquals(80.0, frames.slowestMillis)
        assertEquals(24.0, frames.p50Millis)
    }

    @Test
    fun `a reset clears everything recorded`() {
        recorder.recordTaskStart("task", extraLabel = null)
        clock.advance(150)
        recorder.recordTaskEnd()
        recorder.recordViolation(ViolationKind.Network, "net", appStack)
        recorder.recordFrame(50.0, refreshIntervalMillis = 16.67)

        recorder.reset()

        val report = recorder.buildReport(capabilities)
        assertTrue(report.longTasks.isEmpty() && report.violations.isEmpty() && report.frames.totalFrames == 0)
    }
}

private class FakeClock : MonitorClock {
    private var now = 1_000L

    fun advance(millis: Long) {
        now += millis
    }

    override fun monotonicMillis(): Long = now

    override fun epochMillis(): Long = 1_700_000_000_000 + now
}
