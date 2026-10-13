package com.kitakkun.jetwhale.host.settings.plugin

import com.kitakkun.jetwhale.host.model.MavenCoordinates
import com.kitakkun.jetwhale.host.model.OfficialPlugin
import com.kitakkun.jetwhale.host.model.PluginInstallRequest

sealed interface PluginSettingsScreenAction {
    data class PluginJarSelected(val path: String) : PluginSettingsScreenAction

    data class InstallFromMaven(val coordinates: MavenCoordinates) : PluginSettingsScreenAction

    data class InstallOfficialPlugin(val plugin: OfficialPlugin) : PluginSettingsScreenAction

    data class CancelInstall(val jobId: String) : PluginSettingsScreenAction

    data class RetryInstall(val request: PluginInstallRequest) : PluginSettingsScreenAction

    data class DismissInstall(val jobId: String) : PluginSettingsScreenAction

    /** The user approved a surfaced untrusted jar: pin its hash and load it. */
    data class UntrustedJarApproved(val path: String) : PluginSettingsScreenAction

    /** The user toggled opt-in signing of the plugin trust registry. */
    data class ChangeSignPluginTrustRegistry(val enabled: Boolean) : PluginSettingsScreenAction
}
