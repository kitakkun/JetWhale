package com.kitakkun.jetwhale.debugger.host.settings.plugin

sealed interface PluginSettingsScreenEvent {
    data class PluginJarSelected(val path: String) : PluginSettingsScreenEvent
}
