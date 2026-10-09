package com.kitakkun.jetwhale.plugins.mirror.host

import java.time.Instant
import kotlin.time.Duration

/** A device driven through an XCTest runner, whose stop when idle a lease can hold off. */
internal interface XcTestRunnerDriven {
    /** Keeps the device's runner from stopping when idle for [duration]; returns the time that ends. */
    suspend fun keepRunnerAlive(duration: Duration): Instant

    /** Until when a lease keeps the device's runner from stopping when idle, or null. */
    fun runnerKeptAliveUntil(): Instant?
}
