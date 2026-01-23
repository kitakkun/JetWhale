package com.kitakkun.jetwhale.host.settings.logviewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.architecture.EventEffect
import com.kitakkun.jetwhale.host.architecture.EventFlow
import com.kitakkun.jetwhale.host.model.LogCaptureService
import kotlinx.collections.immutable.toImmutableList

@Composable
fun logViewerScreenPresenter(
    eventFlow: EventFlow<LogViewerScreenEvent>,
    logCaptureService: LogCaptureService,
): LogViewerScreenUiState {
    val logs by logCaptureService.logs.collectAsState()
    var autoScroll by remember { mutableStateOf(true) }
    var filterText by remember { mutableStateOf("") }

    EventEffect(eventFlow) { event ->
        when (event) {
            is LogViewerScreenEvent.ClearLogs -> {
                logCaptureService.clearLogs()
            }
            is LogViewerScreenEvent.UpdateFilterText -> {
                filterText = event.text
            }
            is LogViewerScreenEvent.UpdateAutoScroll -> {
                autoScroll = event.enabled
            }
        }
    }

    val filteredLogs = if (filterText.isBlank()) {
        logs
    } else {
        logs.filter { it.message.contains(filterText, ignoreCase = true) }
    }

    return LogViewerScreenUiState(
        logs = filteredLogs.toImmutableList(),
        autoScroll = autoScroll,
        filterText = filterText,
    )
}
