package com.kitakkun.jetwhale.host.settings.logviewer.components

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class LogEntryRowTest {
    @Test
    fun `the time of day is shown in the given zone rather than in UTC`() {
        val instant = Instant.parse("2026-09-24T16:30:43.497Z")

        assertEquals("01:30:43", instant.timeOfDayIn(TimeZone.of("Asia/Tokyo")))
        assertEquals("16:30:43", instant.timeOfDayIn(TimeZone.UTC))
    }

    @Test
    fun `a time on the whole second keeps its seconds`() {
        assertEquals("09:05:00", Instant.parse("2026-09-24T09:05:00Z").timeOfDayIn(TimeZone.UTC))
    }
}
