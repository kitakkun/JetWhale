package com.kitakkun.jetwhale.debugger.host.settings.plugin

import com.kitakkun.jetwhale.debugger.host.settings.component.PluginInfoUiState
import kotlinx.collections.immutable.ImmutableList

data class PluginSettingsScreenUiState(
    val plugins: ImmutableList<PluginInfoUiState>,
)
