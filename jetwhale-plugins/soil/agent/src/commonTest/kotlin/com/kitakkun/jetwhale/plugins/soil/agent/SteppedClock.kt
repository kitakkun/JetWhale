package com.kitakkun.jetwhale.plugins.soil.agent

import kotlin.time.Clock
import kotlin.time.Instant

/** A clock that stands still until a test moves it. */
internal class SteppedClock(var nowEpochMillis: Long) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(nowEpochMillis)
}
