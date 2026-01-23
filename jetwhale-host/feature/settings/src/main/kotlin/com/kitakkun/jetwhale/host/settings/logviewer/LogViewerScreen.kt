package com.kitakkun.jetwhale.host.settings.logviewer

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.model.LogEntry
import com.kitakkun.jetwhale.host.model.LogLevel
import com.kitakkun.jetwhale.host.settings.logviewer.components.LogListContent
import com.kitakkun.jetwhale.host.settings.logviewer.components.LogViewerToolbar
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Clock

@Composable
fun LogViewerScreen(
    uiState: LogViewerScreenUiState,
    onClearLogs: () -> Unit,
    onFilterTextChange: (String) -> Unit,
    onAutoScrollChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        LogViewerToolbar(
            filterText = uiState.filterText,
            autoScroll = uiState.autoScroll,
            onFilterTextChange = onFilterTextChange,
            onAutoScrollChange = onAutoScrollChange,
            onClearLogs = onClearLogs,
        )

        LogListContent(
            logs = uiState.logs,
            autoScroll = uiState.autoScroll,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview
@Composable
private fun LogViewerScreenPreview() {
    MaterialTheme {
        LogViewerScreen(
            uiState = LogViewerScreenUiState(
                logs = persistentListOf(
                    LogEntry(
                        timestamp = Clock.System.now(),
                        message = "Application started",
                        level = LogLevel.INFO,
                    ),
                    LogEntry(
                        timestamp = Clock.System.now(),
                        message = "Error connecting to server",
                        level = LogLevel.ERROR,
                    ),
                    LogEntry(
                        timestamp = Clock.System.now(),
                        message = "Processing request...",
                        level = LogLevel.INFO,
                    ),
                ),
                autoScroll = true,
                filterText = "",
            ),
            onClearLogs = {},
            onFilterTextChange = {},
            onAutoScrollChange = {},
        )
    }
}
