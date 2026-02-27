package com.kitakkun.jetwhale.host.settings

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.ScreenContext
import com.kitakkun.jetwhale.host.architecture.rememberEventFlow

@Composable
context(_: ScreenContext)
fun SettingsScreenScaffoldRoot(
    onClickClose: () -> Unit,
    content: @Composable (SettingsScreenSegmentedMenu) -> Unit,
) {
    val eventFlow = rememberEventFlow<SettingsScreenScaffoldEvent>()
    val uiState = settingsScreenScaffoldPresenter(
        eventFlow = eventFlow,
    )

    SettingsScreenScaffold(
        uiState = uiState,
        onSelectMenu = { menu ->
            eventFlow.tryEmit(SettingsScreenScaffoldEvent.SelectMenu(menu))
        },
        onClickClose = onClickClose,
    ) { selectedMenu ->
        content(selectedMenu)
    }
}
