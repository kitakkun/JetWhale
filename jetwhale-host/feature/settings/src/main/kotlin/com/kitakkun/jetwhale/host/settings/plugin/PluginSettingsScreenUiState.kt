package com.kitakkun.jetwhale.host.settings.plugin

import com.kitakkun.jetwhale.host.model.PluginInstallProgress
import kotlinx.collections.immutable.ImmutableList

data class PluginSettingsScreenUiState(
    val plugins: ImmutableList<com.kitakkun.jetwhale.host.settings.component.PluginInfoUiState>,
    val failedJarPaths: ImmutableList<String>,
    val untrustedJarPaths: ImmutableList<String>,
    val isInstalling: Boolean = false,
    val installProgress: PluginInstallProgress? = null,
    val installError: String? = null,
)
