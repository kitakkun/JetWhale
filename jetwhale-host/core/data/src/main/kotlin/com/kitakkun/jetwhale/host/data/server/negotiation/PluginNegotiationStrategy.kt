package com.kitakkun.jetwhale.host.data.server.negotiation

import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAgentNegotiationRequest
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleHostNegotiationResponse
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import dev.zacsweers.metro.Inject
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.util.logging.Logger
import kotlinx.coroutines.flow.first

@Inject
class PluginNegotiationStrategy(
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val enabledPluginsRepository: EnabledPluginsRepository,
) : NegotiationStrategy<PluginNegotiationResult> {
    context(logger: Logger)
    override suspend fun DefaultWebSocketServerSession.negotiate(): PluginNegotiationResult {
        val request = receiveDeserialized<JetWhaleAgentNegotiationRequest.AvailablePlugins>()
        val installedPluginsMeta = pluginFactoryRepository.loadedPluginFactories.values.map { it.meta }
        val enabledPluginIds = enabledPluginsRepository.enabledPluginIdsFlow.first()
        sendSerialized(
            JetWhaleHostNegotiationResponse.AvailablePluginsResponse(
                availablePlugins = installedPluginsMeta
                    .filter { meta ->
                        val isRequested = request.plugins.any { it.pluginId == meta.pluginId }
                        val isEnabled = meta.pluginId in enabledPluginIds
                        isRequested && isEnabled // TODO: check version compatibility, too
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
