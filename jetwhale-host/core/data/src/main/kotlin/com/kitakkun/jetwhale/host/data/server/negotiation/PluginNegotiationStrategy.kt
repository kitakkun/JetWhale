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

        val loadedPluginFactories = pluginFactoryRepository.loadedPluginFactories
        val enabledPluginIds = enabledPluginsRepository.enabledPluginIdsFlow.first()

        val availablePlugins = mutableListOf<JetWhalePluginInfo>()
        val incompatiblePlugins = mutableListOf<JetWhalePluginInfo>()

        request.plugins.forEach { requestedPlugin ->
            val factory = loadedPluginFactories[requestedPlugin.pluginId] ?: return@forEach
            val isEnabled = requestedPlugin.pluginId in enabledPluginIds
            val isCompatible = factory.isCompatibleWithAgentPlugin(requestedPlugin.pluginVersion)

            when {
                !isEnabled -> Unit
                !isCompatible -> incompatiblePlugins += requestedPlugin
                else -> availablePlugins += JetWhalePluginInfo(
                    pluginId = factory.meta.pluginId,
                    pluginVersion = factory.meta.version,
                )
            }
        }

        sendSerialized(
            JetWhaleHostNegotiationResponse.AvailablePluginsResponse(
                availablePlugins = availablePlugins,
                incompatiblePlugins = incompatiblePlugins,
            )
        )

        return PluginNegotiationResult(requestedPlugins = request.plugins)
    }
}
