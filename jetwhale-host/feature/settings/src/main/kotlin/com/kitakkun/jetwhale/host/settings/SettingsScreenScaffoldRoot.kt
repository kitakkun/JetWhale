package com.kitakkun.jetwhale.host.settings

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.rememberScreenChannel

@Composable
context(screenContext: SettingsScreenContext)
fun SettingsScreenScaffoldRoot(
    onClickClose: () -> Unit,
    initialMenu: SettingsScreenSegmentedMenu = SettingsScreenSegmentedMenu.General,
    content: @Composable (SettingsScreenSegmentedMenu) -> Unit,
) {
    val screenChannel = rememberScreenChannel<SettingsScreenScaffoldAction, Nothing>()
    val uiState = context(screenContext.presenterContext) {
        settingsScreenScaffoldPresenter(
            screenChannel = screenChannel,
            initialMenu = initialMenu,
        )
    }

    SettingsScreenScaffold(
        uiState = uiState,
        onSelectMenu = { menu ->
            screenChannel.send(SettingsScreenScaffoldAction.SelectMenu(menu))
        },
        onClickClose = onClickClose,
    ) { selectedMenu ->
        content(selectedMenu)
    }
}
