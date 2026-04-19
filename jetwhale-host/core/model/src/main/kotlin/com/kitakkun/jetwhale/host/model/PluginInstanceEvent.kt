package com.kitakkun.jetwhale.host.model

sealed interface PluginInstanceEvent {
    data class Ready(val pluginId: String, val sessionId: String) : PluginInstanceEvent
    data class Disposed(val pluginId: String, val sessionId: String) : PluginInstanceEvent
}
