package com.kitakkun.jetwhale.host.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.architecture.ActionEffect
import com.kitakkun.jetwhale.host.architecture.ScreenChannel

sealed interface SettingsScreenScaffoldAction {
    data class SelectPage(val page: SettingsScreenPage) : SettingsScreenScaffoldAction

    data class ToggleSection(val section: SettingsScreenSection) : SettingsScreenScaffoldAction
}

@Composable
context(_: SettingsPresenterContext)
fun settingsScreenScaffoldPresenter(
    screenChannel: ScreenChannel<SettingsScreenScaffoldAction, Nothing>,
    initialPage: SettingsScreenPage,
): SettingsScreenScaffoldUiState {
    var selectedPage by retain { mutableStateOf(initialPage) }
    var expandedSections by retain { mutableStateOf(setOf(initialPage.section)) }

    ActionEffect(screenChannel) { action ->
        when (action) {
            is SettingsScreenScaffoldAction.SelectPage -> {
                selectedPage = action.page
            }

            is SettingsScreenScaffoldAction.ToggleSection -> {
                expandedSections = if (action.section in expandedSections) {
                    expandedSections - action.section
                } else {
                    expandedSections + action.section
                }
            }
        }
    }

    return SettingsScreenScaffoldUiState(
        selectedPage = selectedPage,
        expandedSections = expandedSections,
    )
}
