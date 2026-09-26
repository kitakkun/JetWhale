package com.kitakkun.jetwhale.plugins.mainthread.host

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TimeOfDay: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

internal fun formatMillis(millis: Long): String = when {
    millis < 1_000 -> "$millis ms"
    else -> String.format(Locale.ROOT, "%.2f s", millis / 1_000.0)
}

internal fun formatFrameMillis(millis: Double): String = String.format(Locale.ROOT, "%.1f ms", millis)

internal fun formatTimeOfDay(epochMillis: Long): String = TimeOfDay.format(Instant.ofEpochMilli(epochMillis))
