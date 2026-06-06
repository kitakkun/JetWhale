package com.kitakkun.jetwhale.host.settings.plugin

sealed interface PluginSettingsScreenAction {
    data class PluginJarSelected(val path: String) : PluginSettingsScreenAction
}
