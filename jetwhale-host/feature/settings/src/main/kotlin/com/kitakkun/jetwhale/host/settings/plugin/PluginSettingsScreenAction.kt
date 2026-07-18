package com.kitakkun.jetwhale.host.settings.plugin

import com.kitakkun.jetwhale.host.model.MavenCoordinates

sealed interface PluginSettingsScreenAction {
    data class PluginJarSelected(val path: String) : PluginSettingsScreenAction
    data class InstallFromMaven(val coordinates: MavenCoordinates) : PluginSettingsScreenAction

    /** The user approved a surfaced untrusted jar: pin its hash and load it. */
    data class UntrustedJarApproved(val path: String) : PluginSettingsScreenAction
}
