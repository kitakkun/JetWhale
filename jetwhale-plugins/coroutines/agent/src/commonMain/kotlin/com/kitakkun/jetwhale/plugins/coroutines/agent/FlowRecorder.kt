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
private const val MAX_VALUE_TEXT = 200

/**
 * The window the emission rate is measured over, in one-second buckets: memory stays bounded however
 * fast a flow emits, and the rate is never capped.
 */
private const val RATE_WINDOW_SECONDS = 10

internal fun <T> trackedFlow(upstream: Flow<T>, recorder: FlowRecorder): Flow<T> = flow {
    recorder.onCollectionStarted()
    try {
        upstream.collect { value ->
            recorder.onEmission(value.toString())
            emit(value)
        }
        recorder.onCompleted()
    } catch (e: CancellationException) {
        recorder.onCancelled()
        throw e
    } catch (e: Throwable) {
        recorder.onFailed()
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

    fun onCollectionStarted() {
        activeCollectors.incrementAndFetch()
        collections.incrementAndFetch()
    }

    fun onEmission(text: String) {
        val now = nowEpochMillis()
        emissions.incrementAndFetch()
        val value = FlowValue(atEpochMillis = now, text = text.take(MAX_VALUE_TEXT))
        recentValues.updateAndGet { (listOf(value) + it).take(MAX_RECENT_VALUES) }
        val second = now / 1000
        rateBuckets.updateAndGet { buckets ->
            val last = buckets.lastOrNull()
            val counted = if (last?.second == second) buckets.dropLast(1) + last.copy(count = last.count + 1) else buckets + RateBucket(second, 1)
            counted.filter { it.second > second - RATE_WINDOW_SECONDS }
        }
    }

    fun onCompleted() {
        activeCollectors.decrementAndFetch()
        completions.incrementAndFetch()
    }

    fun onCancelled() {
        activeCollectors.decrementAndFetch()
        cancellations.incrementAndFetch()
    }

    fun onFailed() {
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
            emissionsPerSecond = rateBuckets.load().filter { it.second > now / 1000 - RATE_WINDOW_SECONDS }.sumOf(RateBucket::count).toDouble() / RATE_WINDOW_SECONDS,
            recentValues = recentValues.load(),
        )
    }
}

private data class RateBucket(val second: Long, val count: Long)
