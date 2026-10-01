package com.kitakkun.jetwhale.host.data.server.negotiation

import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.newestFor
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

        val enabledPluginIds = enabledPluginsRepository.enabledPluginIdsFlow.first()

        val availablePlugins = mutableListOf<JetWhalePluginInfo>()
        val incompatiblePlugins = mutableListOf<JetWhalePluginInfo>()

        val loadedVersions = pluginFactoryRepository.loadedPluginVersions
        request.plugins.forEach { requestedPlugin ->
            val versions = loadedVersions[requestedPlugin.pluginId] ?: return@forEach
            if (requestedPlugin.pluginId !in enabledPluginIds) return@forEach
            // The same rule the instance service binds the session with, so the version reported here
            // is the one the session gets.
            when (val bound = versions.newestFor(requestedPlugin.pluginVersion)) {
                null -> incompatiblePlugins += requestedPlugin

                else -> availablePlugins += JetWhalePluginInfo(
                    pluginId = bound.manifest.pluginId,
                    pluginVersion = bound.manifest.version,
                )
            }
        }

        sendSerialized(
            JetWhaleHostNegotiationResponse.AvailablePluginsResponse(
                availablePlugins = availablePlugins,
                incompatiblePlugins = incompatiblePlugins,
            ),
        )

        return PluginNegotiationResult(requestedPlugins = request.plugins)
    }
}
