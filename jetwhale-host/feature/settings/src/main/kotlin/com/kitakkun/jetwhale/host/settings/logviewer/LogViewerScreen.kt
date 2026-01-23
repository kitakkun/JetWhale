package com.kitakkun.jetwhale.host.settings.logviewer

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.LogEntry
import com.kitakkun.jetwhale.host.model.LogLevel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.Clock

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
        // Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.filterText,
                    onValueChange = onFilterTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Filter logs...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Filter")
                    },
                    trailingIcon = {
                        if (uiState.filterText.isNotEmpty()) {
                            IconButton(onClick = { onFilterTextChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear filter")
                            }
                        }
                    },
                    singleLine = true,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = uiState.autoScroll,
                        onCheckedChange = onAutoScrollChange,
                    )
                    Text("Auto-scroll")
                }

                Button(onClick = onClearLogs) {
                    Text("Clear Logs")
                }
            }
        }

        // Log content
        val listState = rememberLazyListState()

        LaunchedEffect(uiState.logs.size) {
            if (uiState.autoScroll && uiState.logs.isNotEmpty()) {
                listState.animateScrollToItem(uiState.logs.size - 1)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (uiState.logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No logs captured yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = uiState.logs,
                        key = { "${it.timestamp}-${it.message.hashCode()}" }
                    ) { logEntry ->
                        LogEntryRow(logEntry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(logEntry: LogEntry) {
    val backgroundColor = when (logEntry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        LogLevel.INFO -> MaterialTheme.colorScheme.surface
    }

    val textColor = when (logEntry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = logEntry.timestamp.toString().substringAfter("T").substringBefore("."),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = logEntry.level.name,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = logEntry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = textColor,
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
