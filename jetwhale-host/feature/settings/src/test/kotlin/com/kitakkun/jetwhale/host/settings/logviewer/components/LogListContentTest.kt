package com.kitakkun.jetwhale.host.settings.logviewer.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.model.LogEntry
import com.kitakkun.jetwhale.host.model.LogLevel
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.time.Instant

class LogListContentTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the same line captured twice at the same instant is listed twice`() = runComposeUiTest {
        val capturedAt = Instant.parse("2026-10-02T09:00:00Z")
        val logs = persistentListOf(
            LogEntry(id = 1, timestamp = capturedAt, message = "\tat Example.recurse(Example.kt:3)", level = LogLevel.ERROR),
            LogEntry(id = 2, timestamp = capturedAt, message = "\tat Example.recurse(Example.kt:3)", level = LogLevel.ERROR),
        )

        setContent {
            JwTheme(darkTheme = false) {
                LogListContent(logs = logs, autoScroll = false)
            }
        }

        onAllNodesWithText("\tat Example.recurse(Example.kt:3)").assertCountEquals(2)
    }
}
