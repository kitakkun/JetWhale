package com.kitakkun.jetwhale.host.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.architecture.EventEffect
import com.kitakkun.jetwhale.host.architecture.EventFlow
import io.github.takahirom.rin.rememberRetained

interface SettingsScreenScaffoldEvent {
    data class SelectMenu(val menu: SettingsScreenSegmentedMenu) : SettingsScreenScaffoldEvent
}

@Composable
fun settingsScreenScaffoldPresenter(
    eventFlow: EventFlow<SettingsScreenScaffoldEvent>,
): SettingsScreenScaffoldUiState {
    var selectedMenu by rememberRetained { mutableStateOf(SettingsScreenSegmentedMenu.General) }

    EventEffect(eventFlow) { event ->
        when (event) {
            is SettingsScreenScaffoldEvent.SelectMenu -> {
                selectedMenu = event.menu
            }
        }
    }

    return SettingsScreenScaffoldUiState(
        selectedMenu = selectedMenu,
    )
}
