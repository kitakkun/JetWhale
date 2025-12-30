package com.kitakkun.jetwhale.host.settings.plugin

sealed interface PluginSettingsScreenEvent {
    data class PluginJarSelected(val path: String) : PluginSettingsScreenEvent
}
