package com.kitakkun.jetwhale.debugger.host.settings

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.debugger.host.architecture.ScreenContext
import com.kitakkun.jetwhale.debugger.host.architecture.rememberEventFlow

context(_: ScreenContext)
@Composable
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
