package com.kitakkun.jetwhale.host.settings.logviewer.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.model.LogEntry
import com.kitakkun.jetwhale.host.model.LogLevel
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import java.util.TimeZone as JavaTimeZone

class LogEntryRowTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a row shows its time in the machine's time zone`() {
        val previous = JavaTimeZone.getDefault()
        JavaTimeZone.setDefault(JavaTimeZone.getTimeZone("Asia/Tokyo"))
        try {
            runComposeUiTest {
                setContent {
                    JwTheme(darkTheme = false) {
                        LogEntryRow(LogEntry(timestamp = Instant.parse("2026-09-24T16:30:43.497Z"), message = "hello", level = LogLevel.INFO))
                    }
                }

                onNodeWithText("01:30:43").assertExists()
            }
        } finally {
            JavaTimeZone.setDefault(previous)
        }
    }

    @Test
    fun `a time on the whole second keeps its seconds`() {
        assertEquals("09:05:00", Instant.parse("2026-09-24T09:05:00Z").timeOfDayIn(TimeZone.UTC))
    }
}
