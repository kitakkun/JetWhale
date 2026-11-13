package com.kitakkun.jetwhale.plugins.background.host

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.background.protocol.WorkState
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class WorkTableTest {
    @Test
    fun `the earliest run shows its date as well as its time`() = runComposeUiTest {
        val dueAt = ZonedDateTime.of(2026, 3, 14, 9, 26, 53, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        val item = workItem("WorkManager", "sync", WorkState.Enqueued, tags = emptyList(), canRunNow = true).copy(nextRunEpochMillis = dueAt)
        setContent {
            JwTheme(darkTheme = false) {
                WorkTable(items = listOf(item), selectedKey = null, onSelect = {})
            }
        }

        onNodeWithText("03-14 09:26:53").assertExists()
    }
}
