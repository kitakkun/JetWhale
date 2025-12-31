package com.kitakkun.jetwhale.host.settings.plugin

import kotlinx.collections.immutable.ImmutableList

data class PluginSettingsScreenUiState(
    val plugins: ImmutableList<com.kitakkun.jetwhale.host.settings.component.PluginInfoUiState>,
)
