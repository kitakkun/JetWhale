package com.kitakkun.jetwhale.host.settings.plugin

import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.OfficialPlugin
import com.kitakkun.jetwhale.host.model.PluginInstallJob
import com.kitakkun.jetwhale.host.settings.component.PluginInfoUiState
import kotlinx.collections.immutable.ImmutableList

data class PluginSettingsScreenUiState(
    val plugins: ImmutableList<PluginInfoUiState>,
    val officialPlugins: ImmutableList<OfficialPluginUiState>,
    val failedJars: ImmutableList<FailedPluginJar>,
    val untrustedJarPaths: ImmutableList<String>,
    val signPluginTrustRegistry: Boolean,
    val installJobs: ImmutableList<PluginInstallJob>,
    val isAddingFromFile: Boolean,
    val addFromFileError: String?,
)

/** @property installJob the install of this plugin that is queued, running or failed, if any. */
data class OfficialPluginUiState(
    val plugin: OfficialPlugin,
    val isInstalled: Boolean,
    val installJob: PluginInstallJob?,
)
