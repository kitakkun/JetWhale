package com.kitakkun.jetwhale.host.settings

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.rememberScreenChannel

@Composable
context(screenContext: SettingsScreenContext)
fun SettingsScreenScaffoldRoot(
    onClickClose: () -> Unit,
    initialPage: SettingsScreenPage = SettingsScreenPage.Appearance,
    content: @Composable (SettingsScreenPage) -> Unit,
) {
    val screenChannel = rememberScreenChannel<SettingsScreenScaffoldAction, Nothing>()
    val uiState = context(screenContext.presenterContext) {
        settingsScreenScaffoldPresenter(
            screenChannel = screenChannel,
            initialPage = initialPage,
        )
    }

    SettingsScreenScaffold(
        uiState = uiState,
        onSelectPage = { screenChannel.send(SettingsScreenScaffoldAction.SelectPage(it)) },
        onToggleSection = { screenChannel.send(SettingsScreenScaffoldAction.ToggleSection(it)) },
        onClickClose = onClickClose,
        content = content,
    )
}
