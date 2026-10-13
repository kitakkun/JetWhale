package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.FlowValue
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.cancellation.CancellationException

/** Recent values kept per flow, and the longest text kept of each. */
private const val MAX_RECENT_VALUES = 20
private const val MAX_VALUE_TEXT_LENGTH = 200

/**
 * The window the emission rate is measured over, in one-second buckets: memory stays bounded however
 * fast a flow emits, and the rate is never capped.
 */
private const val RATE_WINDOW_SECONDS = 10

internal fun <T> trackedFlow(upstream: Flow<T>, recorder: FlowRecorder): Flow<T> = flow {
    recorder.recordCollectionStart()
    try {
        upstream.collect { value ->
            recorder.recordEmission(value)
            emit(value)
        }
        recorder.recordCompletion()
    } catch (e: CancellationException) {
        recorder.recordCancellation()
        throw e
    } catch (e: Throwable) {
        recorder.recordFailure()
        throw e
    }
}

/** The numbers behind one tracked flow; updated from whichever threads collect it. */
@OptIn(ExperimentalAtomicApi::class)
internal class FlowRecorder(val name: String) {
    private val activeCollectors = AtomicInt(0)
    private val collections = AtomicLong(0)
    private val completions = AtomicLong(0)
    private val cancellations = AtomicLong(0)
    private val failures = AtomicLong(0)
    private val emissions = AtomicLong(0)
    private val recentValues = AtomicReference(emptyList<FlowValue>())
    private val rateBuckets = AtomicReference(emptyList<RateBucket>())

    fun recordCollectionStart() {
        activeCollectors.incrementAndFetch()
        collections.incrementAndFetch()
    }

    fun recordEmission(value: Any?) {
        val now = nowEpochMillis()
        emissions.incrementAndFetch()
        // A value's toString() is the app's code; one that throws costs the record its text,
        // never the collector its value.
        @Suppress("KOTRAIL_CATCH_TOO_BROAD")
        val text = try {
            value.toString()
        } catch (e: Exception) {
            "<toString() threw ${e::class.simpleName}>"
        }
        val recent = FlowValue(atEpochMillis = now, text = text.take(MAX_VALUE_TEXT_LENGTH))
        recentValues.updateAndGet { (listOf(recent) + it).take(MAX_RECENT_VALUES) }
        val epochSecond = now / 1000
        rateBuckets.updateAndGet { it.countingEmissionAt(epochSecond) }
    }

    fun recordCompletion() {
        activeCollectors.decrementAndFetch()
        completions.incrementAndFetch()
    }

    fun recordCancellation() {
        activeCollectors.decrementAndFetch()
        cancellations.incrementAndFetch()
    }

    fun recordFailure() {
        activeCollectors.decrementAndFetch()
        failures.incrementAndFetch()
    }

    fun snapshot(): TrackedFlowInfo {
        val now = nowEpochMillis()
        return TrackedFlowInfo(
            name = name,
            activeCollectors = activeCollectors.load(),
            collections = collections.load(),
            completions = completions.load(),
            cancellations = cancellations.load(),
            failures = failures.load(),
            emissions = emissions.load(),
            emissionsPerSecond = rateBuckets.load().filter { it.epochSecond > now / 1000 - RATE_WINDOW_SECONDS }.sumOf(RateBucket::count).toDouble() / RATE_WINDOW_SECONDS,
            recentValues = recentValues.load().sortedByDescending(FlowValue::atEpochMillis),
        )
    }
}

internal data class RateBucket(val epochSecond: Long, val count: Long)

/**
 * These buckets with one more emission in [epochSecond], and those older than the rate window, which
 * ends at the newest second seen, dropped.
 * Collectors on different threads can commit out of order, so the bucket is found by its second
 * rather than assumed to be the last one; there is never more than one bucket per second.
 */
internal fun List<RateBucket>.countingEmissionAt(epochSecond: Long): List<RateBucket> {
    val counted = if (any { it.epochSecond == epochSecond }) {
        map { if (it.epochSecond == epochSecond) it.copy(count = it.count + 1) else it }
    } else {
        this + RateBucket(epochSecond, 1)
    }
    val newest = maxOf(epochSecond, maxOfOrNull(RateBucket::epochSecond) ?: epochSecond)
    return counted.filter { it.epochSecond > newest - RATE_WINDOW_SECONDS }
}
