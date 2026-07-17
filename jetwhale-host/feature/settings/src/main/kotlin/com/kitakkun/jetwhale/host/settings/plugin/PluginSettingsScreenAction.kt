package com.kitakkun.jetwhale.host.settings.plugin

import com.kitakkun.jetwhale.host.model.MavenCoordinates

sealed interface PluginSettingsScreenAction {
    data class PluginJarSelected(val path: String) : PluginSettingsScreenAction
    data class InstallFromMaven(val coordinates: MavenCoordinates) : PluginSettingsScreenAction
}
