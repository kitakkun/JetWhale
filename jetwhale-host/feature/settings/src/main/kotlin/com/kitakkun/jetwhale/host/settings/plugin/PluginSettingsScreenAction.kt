package com.kitakkun.jetwhale.host.settings.plugin

sealed interface PluginSettingsScreenAction {
    data class PluginJarSelected(val path: String) : PluginSettingsScreenAction

    /** The user approved a surfaced untrusted jar: pin its hash and load it. */
    data class UntrustedJarApproved(val path: String) : PluginSettingsScreenAction
}
