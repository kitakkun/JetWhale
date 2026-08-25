package com.kitakkun.jetwhale.host.model

sealed interface PluginInstanceEvent {
    /** [sessionId] is null for the single instance of a host-scoped plugin. */
    data class Ready(val pluginId: String, val sessionId: String?) : PluginInstanceEvent

    /** [sessionId] is null for the single instance of a host-scoped plugin. */
    data class Disposed(val pluginId: String, val sessionId: String?) : PluginInstanceEvent
}
