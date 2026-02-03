package com.kitakkun.jetwhale.host.data.server.negotiation

import com.kitakkun.jetwhale.host.model.PluginRepository
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAgentNegotiationRequest
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleHostNegotiationResponse
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import dev.zacsweers.metro.Inject
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.util.logging.Logger

@Inject
class PluginNegotiationStrategy(
    private val pluginRepository: PluginRepository,
) : NegotiationStrategy<PluginNegotiationResult> {
    context(logger: Logger)
    override suspend fun DefaultWebSocketServerSession.negotiate(): PluginNegotiationResult {
        val request = receiveDeserialized<JetWhaleAgentNegotiationRequest.AvailablePlugins>()
        val installedPluginsMeta = pluginRepository.loadedPluginFactories.values.map { it.meta }
        sendSerialized(
            JetWhaleHostNegotiationResponse.AvailablePluginsResponse(
                availablePlugins = installedPluginsMeta
                    .filter { meta ->
                        request.plugins.any { it.pluginId == meta.pluginId } // TODO: check version compatibility, too
                    }
                    .map {
                        JetWhalePluginInfo(
                            pluginId = it.pluginId,
                            pluginVersion = it.version,
                        )
                    },
                incompatiblePlugins = listOf(), // TODO: Provide actual incompatible plugins
            )
        )
        return PluginNegotiationResult(requestedPlugins = request.plugins)
    }
}
