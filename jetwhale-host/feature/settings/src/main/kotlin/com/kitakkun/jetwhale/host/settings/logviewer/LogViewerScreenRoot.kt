package com.kitakkun.jetwhale.host.settings.logviewer

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.rememberScreenChannel
import com.kitakkun.jetwhale.host.settings.SettingsScreenContext

@Composable
context(screenContext: SettingsScreenContext)
fun LogViewerScreenRoot() {
    val screenChannel = rememberScreenChannel<LogViewerScreenAction, Nothing>()
    val uiState = context(screenContext.presenterContext) {
        logViewerScreenPresenter(
            screenChannel = screenChannel,
        )
    }

    LogViewerScreen(
        uiState = uiState,
        onClearLogs = {
            screenChannel.send(LogViewerScreenAction.ClearLogs)
        },
        onFilterTextChange = {
            screenChannel.send(LogViewerScreenAction.UpdateFilterText(it))
        },
        onAutoScrollChange = {
            screenChannel.send(LogViewerScreenAction.UpdateAutoScroll(it))
        },
    )
}
