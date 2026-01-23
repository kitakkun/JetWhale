package com.kitakkun.jetwhale.host.settings.logviewer

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.rememberEventFlow

context(screenContext: com.kitakkun.jetwhale.host.settings.SettingsScreenContext)
@Composable
fun LogViewerScreenRoot() {
    val eventFlow = rememberEventFlow<LogViewerScreenEvent>()
    val uiState = logViewerScreenPresenter(
        eventFlow = eventFlow,
        logCaptureService = screenContext.logCaptureService,
    )

    LogViewerScreen(
        uiState = uiState,
        onClearLogs = {
            eventFlow.tryEmit(LogViewerScreenEvent.ClearLogs)
        },
        onFilterTextChange = {
            eventFlow.tryEmit(LogViewerScreenEvent.UpdateFilterText(it))
        },
        onAutoScrollChange = {
            eventFlow.tryEmit(LogViewerScreenEvent.UpdateAutoScroll(it))
        },
    )
}
