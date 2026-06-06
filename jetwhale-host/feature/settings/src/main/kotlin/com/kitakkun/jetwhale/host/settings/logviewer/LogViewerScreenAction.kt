package com.kitakkun.jetwhale.host.settings.logviewer

sealed interface LogViewerScreenAction {
    data object ClearLogs : LogViewerScreenAction
    data class UpdateFilterText(val text: String) : LogViewerScreenAction
    data class UpdateAutoScroll(val enabled: Boolean) : LogViewerScreenAction
}
