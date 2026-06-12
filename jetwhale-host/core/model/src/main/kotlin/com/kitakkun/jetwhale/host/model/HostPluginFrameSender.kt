package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.protocol.messaging.PluginFrame

/**
 * Sends a plugin messaging [PluginFrame] to a specific debug session. The plugin messaging peers
 * owned by [PluginInstanceService] use this to deliver their outbound frames over the WebSocket,
 * decoupling instance management from the transport.
 */
interface HostPluginFrameSender {
    suspend fun sendFrame(sessionId: String, frame: PluginFrame)
}
