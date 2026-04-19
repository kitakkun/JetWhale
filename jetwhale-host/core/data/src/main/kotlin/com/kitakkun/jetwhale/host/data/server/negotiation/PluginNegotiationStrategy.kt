package com.kitakkun.jetwhale.host.data.server.negotiation

import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
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

        val loadedPlugins = pluginFactoryRepository.loadedPlugins
        val enabledPluginIds = enabledPluginsRepository.enabledPluginIdsFlow.first()

        val availablePlugins = mutableListOf<JetWhalePluginInfo>()
        val incompatiblePlugins = mutableListOf<JetWhalePluginInfo>()

        request.plugins.forEach { requestedPlugin ->
            val loaded = loadedPlugins[requestedPlugin.pluginId] ?: return@forEach
            val isEnabled = requestedPlugin.pluginId in enabledPluginIds
            val isCompatible = loaded.manifest.agentVersionRange
                ?.isCompatibleWith(requestedPlugin.pluginVersion)
                ?: true

            when {
                !isEnabled -> Unit

                !isCompatible -> incompatiblePlugins += requestedPlugin

                else -> availablePlugins += JetWhalePluginInfo(
                    pluginId = loaded.manifest.pluginId,
                    pluginVersion = loaded.manifest.version,
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

private fun JetWhaleHostPluginManifest.AgentVersionRange.isCompatibleWith(version: String): Boolean {
    val v = version.toVersionParts()
    return (min?.let { compareVersionParts(v, it.toVersionParts()) >= 0 } ?: true) &&
        (max?.let { compareVersionParts(v, it.toVersionParts()) <= 0 } ?: true)
}

private fun compareVersionParts(a: List<Int>, b: List<Int>): Int {
    val maxLen = maxOf(a.size, b.size)
    for (i in 0 until maxLen) {
        val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (diff != 0) return diff
    }
    return 0
}

private fun String.toVersionParts(): List<Int> = split(".").map { it.toIntOrNull() ?: 0 }
