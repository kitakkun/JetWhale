package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.JetWhaleDebugOperationContextQueryKey
import com.kitakkun.jetwhale.host.model.PluginIdQualifier
import com.kitakkun.jetwhale.host.model.SessionIdQualifier
import com.kitakkun.jetwhale.host.sdk.JetWhaleDebugOperationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import soil.query.QueryId
import soil.query.buildQueryKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultJetWhaleDebugOperationContextQueryKey(
    @param:PluginIdQualifier private val pluginId: String,
    @param:SessionIdQualifier private val sessionId: String,
    private val debugWebSocketServer: DebugWebSocketServer,
) : JetWhaleDebugOperationContextQueryKey by buildQueryKey(
    id = QueryId("jetwhale_debug_operation_context_${pluginId}_$sessionId"),
    fetch = {
        object : JetWhaleDebugOperationContext<String, String> {
            override val coroutineScope: CoroutineScope = debugWebSocketServer.getCoroutineScopeForSession(sessionId)
            override suspend fun dispatch(method: String): String? {
                return debugWebSocketServer.sendMethod(
                    pluginId = pluginId,
                    sessionId = sessionId,
                    payload = method
                )
            }
        }
    }
)
