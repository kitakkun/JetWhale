package com.kitakkun.jetwhale.host.settings.plugin

import com.kitakkun.jetwhale.host.model.MavenCoordinates

sealed interface PluginSettingsScreenEvent {
    data class PluginJarSelected(val path: String) : PluginSettingsScreenEvent
    data class InstallFromMaven(val coordinates: MavenCoordinates) : PluginSettingsScreenEvent
}
