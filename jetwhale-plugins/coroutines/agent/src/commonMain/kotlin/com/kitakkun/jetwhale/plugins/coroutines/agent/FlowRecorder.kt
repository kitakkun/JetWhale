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

/** The window the emission rate is measured over. */
private const val RATE_WINDOW_MILLIS = 10_000L

/** Upper bound on the timestamps kept for the rate, so a flow emitting very fast costs bounded memory. */
private const val MAX_RATE_SAMPLES = 2_000

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
    private val emissionTimes = AtomicReference(emptyList<Long>())

    fun onCollectionStarted() {
        activeCollectors.incrementAndFetch()
        collections.incrementAndFetch()
    }

    fun onEmission(text: String) {
        val now = nowEpochMillis()
        emissions.incrementAndFetch()
        val value = FlowValue(atEpochMillis = now, text = text.take(MAX_VALUE_TEXT))
        recentValues.updateAndGet { (listOf(value) + it).take(MAX_RECENT_VALUES) }
        emissionTimes.updateAndGet { times -> (times.filter { it > now - RATE_WINDOW_MILLIS } + now).takeLast(MAX_RATE_SAMPLES) }
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
            emissionsPerSecond = emissionTimes.load().count { it > now - RATE_WINDOW_MILLIS } * 1000.0 / RATE_WINDOW_MILLIS,
            recentValues = recentValues.load(),
        )
    }
}
