package com.kitakkun.jetwhale.host.model

sealed interface PluginInstanceEvent {
    /** @property version The version of the plugin the instance was created from. */
    data class Ready(val pluginId: String, val sessionId: String, val version: String) : PluginInstanceEvent
    data class Disposed(val pluginId: String, val sessionId: String) : PluginInstanceEvent
}
