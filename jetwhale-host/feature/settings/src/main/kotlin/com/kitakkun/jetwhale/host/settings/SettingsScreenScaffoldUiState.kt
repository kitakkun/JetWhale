package com.kitakkun.jetwhale.host.settings

data class SettingsScreenScaffoldUiState(
    val selectedPage: SettingsScreenPage,
    val expandedSections: Set<SettingsScreenSection>,
)
