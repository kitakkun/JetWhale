package com.kitakkun.jetwhale.host.settings.logviewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.architecture.ActionEffect
import com.kitakkun.jetwhale.host.architecture.ScreenChannel
import com.kitakkun.jetwhale.host.settings.SettingsPresenterContext
import kotlinx.collections.immutable.toImmutableList

@Composable
context(presenterContext: SettingsPresenterContext)
fun logViewerScreenPresenter(
    screenChannel: ScreenChannel<LogViewerScreenAction, Nothing>,
): LogViewerScreenUiState {
    val logCaptureService = presenterContext.logCaptureService
    val logs by logCaptureService.logs.collectAsState()
    var autoScroll by remember { mutableStateOf(true) }
    var filterText by remember { mutableStateOf("") }

    ActionEffect(screenChannel) { action ->
        when (action) {
            is LogViewerScreenAction.ClearLogs -> {
                logCaptureService.clearLogs()
            }

            is LogViewerScreenAction.UpdateFilterText -> {
                filterText = action.text
            }

            is LogViewerScreenAction.UpdateAutoScroll -> {
                autoScroll = action.enabled
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
