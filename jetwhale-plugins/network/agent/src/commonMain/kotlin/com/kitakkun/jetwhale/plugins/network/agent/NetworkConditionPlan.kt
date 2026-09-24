package com.kitakkun.jetwhale.plugins.network.agent

import com.kitakkun.jetwhale.plugins.network.protocol.AppliedNetworkCondition
import com.kitakkun.jetwhale.plugins.network.protocol.InjectedFailure
import com.kitakkun.jetwhale.plugins.network.protocol.MAX_SIMULATED_DELAY_MS
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkConditionRule
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * What a network condition does to one request, decided up front so a transport adapter only has
 * to carry it out: jitter and the failure dice are rolled once, when the plan is made.
 *
 * An adapter fails the request at once when [offline]; otherwise it waits [latency], then, when
 * [failure] is set, waits [failureAfter] and fails with it; otherwise it paces the request body at
 * [uploadBytesPerSecond] and the response body at [downloadBytesPerSecond] (unlimited when null).
 */
class NetworkConditionPlan internal constructor(
    val ruleId: String,
    val ruleName: String,
    val latency: Duration,
    val failure: InjectedFailure?,
    val failureAfter: Duration,
    val offline: Boolean,
    val downloadBytesPerSecond: Long?,
    val uploadBytesPerSecond: Long?,
) {
    /** How the plan is reported on the transaction it shaped. */
    fun applied(): AppliedNetworkCondition = AppliedNetworkCondition(
        ruleId = ruleId,
        ruleName = ruleName,
        addedLatencyMs = latency.inWholeMilliseconds,
        downloadBytesPerSecond = downloadBytesPerSecond,
        uploadBytesPerSecond = uploadBytesPerSecond,
        injectedFailure = failure,
        offline = offline,
    )
}

internal fun NetworkConditionRule.plan(random: Random): NetworkConditionPlan {
    val condition = condition
    if (condition.offline) {
        return NetworkConditionPlan(
            ruleId = id,
            ruleName = name,
            latency = Duration.ZERO,
            failure = null,
            failureAfter = Duration.ZERO,
            offline = true,
            downloadBytesPerSecond = null,
            uploadBytesPerSecond = null,
        )
    }
    // The host rejects out-of-range numbers, but a rule is still clamped here: an unchecked value
    // would otherwise overflow or throw while planning, before any transaction records the failure.
    val maxJitterMs = condition.jitterMs.coerceIn(0, MAX_SIMULATED_DELAY_MS)
    val jitterMs = if (maxJitterMs > 0) random.nextLong(maxJitterMs + 1) else 0L
    val fails = condition.failureRate > 0.0 && random.nextDouble() < condition.failureRate
    return NetworkConditionPlan(
        ruleId = id,
        ruleName = name,
        latency = (condition.latencyMs.coerceIn(0, MAX_SIMULATED_DELAY_MS) + jitterMs).milliseconds,
        failure = if (fails) condition.failure else null,
        failureAfter = condition.failureAfterMs.coerceIn(0, MAX_SIMULATED_DELAY_MS).milliseconds,
        offline = false,
        downloadBytesPerSecond = condition.downloadBytesPerSecond?.takeIf { it > 0 },
        uploadBytesPerSecond = condition.uploadBytesPerSecond?.takeIf { it > 0 },
    )
}

/**
 * Paces a byte stream to [bytesPerSecond]: before each chunk goes out, [delayBefore] says how long
 * to wait so the bytes sent so far never run ahead of the rate. Transport adapters copy a body in
 * chunks of [chunkSize] through it, so a large body arrives steadily rather than all at once after
 * one long pause.
 *
 * Not thread-safe; one pacer serves one body.
 */
class BandwidthPacer(private val bytesPerSecond: Long, timeSource: TimeSource) {
    init {
        require(bytesPerSecond > 0) { "bytesPerSecond must be positive, was $bytesPerSecond" }
    }

    private val start = timeSource.markNow()
    private var bytesSent = 0L

    /** Roughly a twentieth of a second of data, so pacing stays smooth without tiny writes. */
    val chunkSize: Int = (bytesPerSecond / CHUNKS_PER_SECOND).coerceIn(MIN_CHUNK_BYTES, MAX_CHUNK_BYTES).toInt()

    /** Counts [byteCount] as sent and returns how long to wait before sending them. */
    fun delayBefore(byteCount: Int): Duration {
        bytesSent += byteCount
        val due = (bytesSent * MICROS_PER_SECOND / bytesPerSecond).microseconds
        return (due - start.elapsedNow()).coerceAtLeast(Duration.ZERO)
    }
}

private const val CHUNKS_PER_SECOND = 20L
private const val MIN_CHUNK_BYTES = 256L
private const val MAX_CHUNK_BYTES = 64L * 1024
private const val MICROS_PER_SECOND = 1_000_000L
