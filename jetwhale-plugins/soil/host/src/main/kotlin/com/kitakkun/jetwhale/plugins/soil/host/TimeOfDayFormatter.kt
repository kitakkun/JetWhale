package com.kitakkun.jetwhale.plugins.soil.host

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Writes a moment as the wall-clock time in [zoneId]. */
internal class TimeOfDayFormatter(private val zoneId: ZoneId) {
    private val millisecondPattern = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val secondPattern = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** To the millisecond, `14:03:07.215`, as the agent timestamps its events. */
    fun formatTimeOfDay(epochMillis: Long): String = millisecondPattern.format(Instant.ofEpochMilli(epochMillis).atZone(zoneId))

    /** To the second, `14:03:07`, as Soil timestamps its state. */
    fun formatTimeOfDayToTheSecond(epochSeconds: Long): String = secondPattern.format(Instant.ofEpochSecond(epochSeconds).atZone(zoneId))
}
