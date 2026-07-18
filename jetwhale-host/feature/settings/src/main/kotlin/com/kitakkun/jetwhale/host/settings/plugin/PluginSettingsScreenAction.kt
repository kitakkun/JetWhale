package com.kitakkun.jetwhale.host.settings.plugin

import com.kitakkun.jetwhale.host.model.MavenCoordinates
import com.kitakkun.jetwhale.host.model.OfficialPlugin

sealed interface PluginSettingsScreenAction {
    data class PluginJarSelected(val path: String) : PluginSettingsScreenAction
    data class InstallFromMaven(val coordinates: MavenCoordinates) : PluginSettingsScreenAction
    data class InstallOfficialPlugin(val plugin: OfficialPlugin) : PluginSettingsScreenAction

    /** The user approved a surfaced untrusted jar: pin its hash and load it. */
    data class UntrustedJarApproved(val path: String) : PluginSettingsScreenAction
}
