package com.kitakkun.jetwhale.host.settings.logviewer

sealed interface LogViewerScreenEvent {
    data object ClearLogs : LogViewerScreenEvent
    data class UpdateFilterText(val text: String) : LogViewerScreenEvent
    data class UpdateAutoScroll(val enabled: Boolean) : LogViewerScreenEvent
}
