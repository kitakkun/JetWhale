package com.kitakkun.jetwhale.plugins.mainthread.agent

import com.kitakkun.jetwhale.plugins.mainthread.protocol.FrameStats
import com.kitakkun.jetwhale.plugins.mainthread.protocol.Hotspot
import com.kitakkun.jetwhale.plugins.mainthread.protocol.JankyFrame
import com.kitakkun.jetwhale.plugins.mainthread.protocol.LongTask
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MainThreadReport
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorCapabilities
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorSettings
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ViolationGroup
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ViolationKind
import kotlin.time.Clock
import kotlin.time.TimeSource

internal const val LONG_TASK_CAPACITY = 200
internal const val HOTSPOT_CAPACITY = 100
internal const val VIOLATION_GROUP_CAPACITY = 100
internal const val FRAME_SAMPLE_CAPACITY = 1000
internal const val JANKY_FRAME_CAPACITY = 100

/** A frame longer than this many refresh intervals counts as janky. */
private const val JANK_FACTOR = 1.5

/** Two clocks: one that only moves forward, for durations, and wall time, for display. */
internal interface MonitorClock {
    fun monotonicMillis(): Long

    fun epochMillis(): Long
}

internal class SystemMonitorClock : MonitorClock {
    private val origin = TimeSource.Monotonic.markNow()

    override fun monotonicMillis(): Long = origin.elapsedNow().inWholeMilliseconds

    override fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()
}

/**
 * Everything the probes observe on the main thread, kept in bounded buffers. Probes call it from
 * the main thread (task start and end), a sampling thread and listener threads, so every access
 * goes through [lock].
 */
internal class MainThreadRecorder(
    private val clock: MonitorClock,
    initialSettings: MonitorSettings,
) {
    private val lock = monitorLock()

    private var settings: MonitorSettings = initialSettings
    private var recordingSince: Long = clock.epochMillis()

    private val longTasks = ArrayDeque<LongTask>()
    private val hotspots = LinkedHashMap<String, HotspotAccumulator>()
    private val violations = LinkedHashMap<Pair<ViolationKind, String>, ViolationGroup>()
    private val frameDurations = ArrayDeque<Double>()
    private val jankyFrames = ArrayDeque<JankyFrame>()
    private var totalFrames = 0
    private var jankyFrameCount = 0
    private var refreshIntervalMillis = 0.0

    private var running: RunningTask? = null
    private var nextTaskId = 0L

    val currentSettings: MonitorSettings get() = lock.withLock { settings }

    fun updateSettings(newSettings: MonitorSettings): MonitorSettings = lock.withLock {
        settings = newSettings
        settings
    }

    /**
     * [description] is kept as the probe saw it and only turned into a label by [formatTaskLabel]
     * if the task turns out long: most tasks are short, and this runs for every one of them.
     */
    fun taskStarted(description: String, extraLabel: String?) = lock.withLock {
        // A stall ends when the main thread gets back to its queue, which is where this message came from.
        if (running?.isStall == true) finishRunning()
        running = RunningTask(
            id = nextTaskId++,
            description = description,
            extraLabel = extraLabel,
            startMonotonic = clock.monotonicMillis(),
            startEpoch = clock.epochMillis(),
            isStall = false,
        )
    }

    fun taskFinished() = lock.withLock(::finishRunning)

    /**
     * The main thread has been busy for [busyForMillis] without the probe seeing a task start —
     * work the platform runs outside its task hooks, such as Android's input dispatch. It is recorded
     * as a task that began [busyForMillis] ago and ends at [stallEnded] or when the next task starts.
     * While a task is running, the stall is that task and nothing changes.
     */
    fun stallDetected(description: String, busyForMillis: Long) = lock.withLock {
        if (running != null) return@withLock
        running = RunningTask(
            id = nextTaskId++,
            description = description,
            extraLabel = null,
            startMonotonic = clock.monotonicMillis() - busyForMillis,
            startEpoch = clock.epochMillis() - busyForMillis,
            isStall = true,
        )
    }

    fun stallEnded() = lock.withLock {
        if (running?.isStall == true) finishRunning()
    }

    private fun finishRunning() {
        val task = running ?: return
        running = null
        val duration = clock.monotonicMillis() - task.startMonotonic
        if (duration < settings.longTaskThresholdMillis) return
        longTasks.addBounded(
            LongTask(
                startEpochMillis = task.startEpoch,
                durationMillis = duration,
                label = listOfNotNull(formatTaskLabel(task.description), task.extraLabel).joinToString(" · "),
                sampleCount = task.samples,
                unresponsive = duration >= settings.unresponsiveThresholdMillis,
            ),
            LONG_TASK_CAPACITY,
        )
    }

    /**
     * Called on a regular beat from a thread other than the main one: samples the running task's
     * stack once it has run past the threshold, at most once per sample interval. [readStack] is
     * only invoked when a sample is due, since reading another thread's stack is the expensive part.
     */
    fun sampleIfDue(readStack: () -> List<String>) {
        val task = lock.withLock {
            val current = running ?: return
            val now = clock.monotonicMillis()
            val elapsed = now - current.startMonotonic
            if (elapsed < settings.longTaskThresholdMillis) return
            val lastSample = current.lastSampleMonotonic
            if (lastSample != null && now - lastSample < settings.sampleIntervalMillis) return
            current.lastSampleMonotonic = now
            current
        }
        val stack = readStack()
        lock.withLock {
            // The task may have finished while the stack was being read; a stack from the next task
            // must not be attributed to it.
            if (running !== task || stack.isEmpty()) return
            task.samples++
            val signature = stackSignature(stack)
            val accumulator = hotspots.getOrPut(signature) { HotspotAccumulator() }
            accumulator.samples++
            accumulator.frames = stack
            accumulator.taskIds += task.id
            if (hotspots.size > HOTSPOT_CAPACITY) {
                // The rarest hotspot matters least; the one just sampled is never the one dropped,
                // or a full table could never take in anything new.
                val weakest = hotspots.entries.filter { it.key != signature }.minBy { it.value.samples }.key
                hotspots.remove(weakest)
            }
        }
    }

    fun violation(kind: ViolationKind, message: String, stack: List<String>) = lock.withLock {
        val callSite = callSiteOf(stack)
        val key = kind to callSite
        val previous = violations.remove(key)
        violations[key] = ViolationGroup(
            kind = kind,
            callSite = callSite,
            message = message,
            stack = stack,
            count = (previous?.count ?: 0) + 1,
            lastEpochMillis = clock.epochMillis(),
        )
        while (violations.size > VIOLATION_GROUP_CAPACITY) violations.remove(violations.keys.first())
    }

    fun frame(durationMillis: Double, refreshIntervalMillis: Double) = lock.withLock {
        this.refreshIntervalMillis = refreshIntervalMillis
        totalFrames++
        frameDurations.addBounded(durationMillis, FRAME_SAMPLE_CAPACITY)
        if (durationMillis > refreshIntervalMillis * JANK_FACTOR) {
            jankyFrameCount++
            jankyFrames.addBounded(JankyFrame(endEpochMillis = clock.epochMillis(), durationMillis = durationMillis), JANKY_FRAME_CAPACITY)
        }
    }

    fun reset() = lock.withLock {
        recordingSince = clock.epochMillis()
        longTasks.clear()
        hotspots.clear()
        violations.clear()
        frameDurations.clear()
        jankyFrames.clear()
        totalFrames = 0
        jankyFrameCount = 0
    }

    fun report(capabilities: MonitorCapabilities): MainThreadReport = lock.withLock {
        val interval = settings.sampleIntervalMillis
        MainThreadReport(
            capabilities = capabilities,
            settings = settings,
            recordingSinceEpochMillis = recordingSince,
            longTasks = longTasks.toList(),
            hotspots = hotspots.map { (signature, accumulator) ->
                Hotspot(
                    signature = signature,
                    frames = accumulator.frames,
                    sampleCount = accumulator.samples,
                    blockedMillis = accumulator.samples * interval,
                    taskCount = accumulator.taskIds.size,
                )
            }.sortedByDescending(Hotspot::sampleCount),
            violations = violations.values.sortedByDescending(ViolationGroup::count),
            frames = frameStats(),
        )
    }

    private fun frameStats(): FrameStats {
        val sorted = frameDurations.sorted()
        return FrameStats(
            totalFrames = totalFrames,
            jankyFrames = jankyFrameCount,
            refreshIntervalMillis = refreshIntervalMillis,
            p50Millis = sorted.percentile(0.50),
            p90Millis = sorted.percentile(0.90),
            p99Millis = sorted.percentile(0.99),
            slowestMillis = sorted.lastOrNull() ?: 0.0,
            recentJankyFrames = jankyFrames.toList(),
        )
    }

    private class RunningTask(
        val id: Long,
        val description: String,
        val extraLabel: String?,
        val startMonotonic: Long,
        val startEpoch: Long,
        val isStall: Boolean,
    ) {
        var lastSampleMonotonic: Long? = null
        var samples = 0
    }

    private class HotspotAccumulator {
        var samples = 0
        var frames: List<String> = emptyList()
        val taskIds = mutableSetOf<Long>()
    }
}

private fun <T> ArrayDeque<T>.addBounded(element: T, capacity: Int) {
    addLast(element)
    while (size > capacity) removeFirst()
}

private fun List<Double>.percentile(fraction: Double): Double {
    if (isEmpty()) return 0.0
    val index = ((size - 1) * fraction).toInt()
    return this[index]
}
