package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStats
import com.kitakkun.jetwhale.plugins.coroutines.protocol.LongRun
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Runnable
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Passes every task on to [delegate], timing how long it waits and how long it runs. It keeps the
 * default `isDispatchNeeded` of true rather than asking the delegate: a task the delegate would run
 * in place, as `Dispatchers.Main.immediate` does, would bypass `dispatch` and go untimed.
 */
internal class TrackedDispatcher(
    private val delegate: CoroutineDispatcher,
    private val recorder: DispatcherRecorder,
) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val queuedAt = TimeSource.Monotonic.markNow()
        recorder.recordQueuedTask()
        try {
            delegate.dispatch(
                context,
                Runnable {
                    recorder.recordTaskStart(queuedAt.elapsedNow())
                    val startedAt = TimeSource.Monotonic.markNow()
                    try {
                        block.run()
                    } finally {
                        recorder.recordTaskCompletion(startedAt.elapsedNow(), context[CoroutineName]?.name)
                    }
                },
            )
        } catch (e: Throwable) {
            // Only a delegate that throws refuses a task: when an executor-backed dispatcher's
            // executor rejects one, the coroutines library runs the wrapped task elsewhere, so it
            // is still counted as started.
            recorder.recordRejectedTask()
            throw e
        }
    }

    override fun toString(): String = "${recorder.name} (tracked $delegate)"
}

/** The numbers behind one tracked dispatcher; updated from whichever threads it runs tasks on. */
@OptIn(ExperimentalAtomicApi::class)
internal class DispatcherRecorder(
    val name: String,
    private val longRunThreshold: Duration,
) {
    private val queued = AtomicInt(0)
    private val running = AtomicInt(0)
    private val completedTasks = AtomicLong(0)
    private val startedTasks = AtomicLong(0)
    private val totalQueueMicros = AtomicLong(0)
    private val maxQueueMicros = AtomicLong(0)
    private val totalRunMicros = AtomicLong(0)
    private val maxRunMicros = AtomicLong(0)
    private val longRuns = AtomicReference(emptyList<LongRun>())

    fun recordQueuedTask() {
        queued.incrementAndFetch()
    }

    fun recordRejectedTask() {
        queued.decrementAndFetch()
    }

    fun recordTaskStart(queueDuration: Duration) {
        queued.decrementAndFetch()
        running.incrementAndFetch()
        startedTasks.incrementAndFetch()
        val micros = queueDuration.inWholeMicroseconds
        totalQueueMicros.fetchAndAdd(micros)
        maxQueueMicros.raiseTo(micros)
    }

    fun recordTaskCompletion(runDuration: Duration, coroutineName: String?) {
        running.decrementAndFetch()
        completedTasks.incrementAndFetch()
        val micros = runDuration.inWholeMicroseconds
        totalRunMicros.fetchAndAdd(micros)
        maxRunMicros.raiseTo(micros)
        if (runDuration >= longRunThreshold) {
            val run = LongRun(atEpochMillis = nowEpochMillis(), durationMillis = runDuration.toDouble(DurationUnit.MILLISECONDS), coroutineName = coroutineName)
            longRuns.updateAndGet { (listOf(run) + it).take(MAX_LONG_RUNS) }
        }
    }

    /** Forgets the recorded long runs; returns how many there were. */
    fun clearLongRuns(): Int = longRuns.exchange(emptyList()).size

    fun snapshot(): DispatcherStats {
        val startedCount = startedTasks.load()
        val completedCount = completedTasks.load()
        return DispatcherStats(
            name = name,
            queued = queued.load().coerceAtLeast(0),
            running = running.load().coerceAtLeast(0),
            completedTasks = completedCount,
            averageQueueLatencyMillis = if (startedCount == 0L) 0.0 else totalQueueMicros.load() / startedCount / 1000.0,
            maxQueueLatencyMillis = maxQueueMicros.load() / 1000.0,
            averageRunMillis = if (completedCount == 0L) 0.0 else totalRunMicros.load() / completedCount / 1000.0,
            maxRunMillis = maxRunMicros.load() / 1000.0,
            longRunThresholdMillis = longRunThreshold.inWholeMilliseconds,
            longRuns = longRuns.load(),
        )
    }
}

/** How many long runs a dispatcher keeps; older ones make way for newer ones. */
private const val MAX_LONG_RUNS = 50

@OptIn(ExperimentalAtomicApi::class)
private fun AtomicLong.raiseTo(value: Long) {
    while (true) {
        val current = load()
        if (value <= current || compareAndSet(current, value)) return
    }
}
