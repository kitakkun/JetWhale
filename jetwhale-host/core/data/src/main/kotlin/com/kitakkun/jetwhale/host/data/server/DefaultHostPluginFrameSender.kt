package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultHostPluginFrameSender(
    private val ktorWebSocketServer: KtorWebSocketServer,
) : HostPluginFrameSender {
    override suspend fun sendFrame(sessionId: String, frame: PluginFrame) {
        ktorWebSocketServer.sendToSession(
            sessionId = sessionId,
            event = JetWhaleDebuggerEvent.PluginFrameMessage(frame),
        )
    }
}
