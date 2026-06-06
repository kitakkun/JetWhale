package com.kitakkun.jetwhale.host.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.architecture.ActionEffect
import com.kitakkun.jetwhale.host.architecture.ScreenChannel
import io.github.takahirom.rin.rememberRetained

sealed interface SettingsScreenScaffoldAction {
    data class SelectMenu(val menu: SettingsScreenSegmentedMenu) : SettingsScreenScaffoldAction
}

@Composable
context(_: SettingsPresenterContext)
fun settingsScreenScaffoldPresenter(
    screenChannel: ScreenChannel<SettingsScreenScaffoldAction, Nothing>,
    initialMenu: SettingsScreenSegmentedMenu,
): SettingsScreenScaffoldUiState {
    var selectedMenu by rememberRetained { mutableStateOf(initialMenu) }

    ActionEffect(screenChannel) { action ->
        when (action) {
            is SettingsScreenScaffoldAction.SelectMenu -> {
                selectedMenu = action.menu
            }
        }
    }

    return SettingsScreenScaffoldUiState(
        selectedMenu = selectedMenu,
    )
}
