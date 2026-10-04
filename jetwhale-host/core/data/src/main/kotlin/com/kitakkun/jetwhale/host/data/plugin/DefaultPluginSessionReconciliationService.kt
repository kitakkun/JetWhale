package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.PluginReconciliationEvent
import com.kitakkun.jetwhale.host.model.PluginSessionReconciliationService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPluginSessionReconciliationService(
    private val sessionRepository: DebugSessionRepository,
    private val enabledPluginsRepository: EnabledPluginsRepository,
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val pluginInstanceService: PluginInstanceService,
) : PluginSessionReconciliationService {
    override fun requiresAgent(pluginId: String): Boolean = pluginFactoryRepository.loadedPlugins[pluginId]?.manifest?.requiresAgent ?: true

    override fun targetSessions(pluginId: String, sessions: List<DebugSession>): Map<String, String?> = if (requiresAgent(pluginId)) {
        sessions.mapNotNull { session ->
            session.installedPlugins.firstOrNull { it.pluginId == pluginId }?.let { advertised -> session.id to advertised.pluginVersion }
        }.toMap()
    } else {
        mapOf(HostSession.ID to null)
    }

    override fun reconciliationEvents(): Flow<PluginReconciliationEvent> = channelFlow {
        launch {
            combine(
                enabledPluginsRepository.enabledPluginIdsFlow,
                sessionRepository.debugSessionsFlow.map { sessions -> sessions.filter(DebugSession::isActive) },
                // A load is a reconciliation trigger of its own: nothing removes an id from the
                // enabled set when its jar goes, so installing a jar whose pluginId is already
                // enabled changes neither flow above.
                pluginFactoryRepository.loadedPluginVersionsFlow,
            ) { enabledPluginIds, activeSessions, _ -> enabledPluginIds to activeSessions }
                .collect { (enabledPluginIds, activeSessions) ->
                    enabledPluginIds.forEach { pluginId ->
                        val activatedSessionIds = pluginInstanceService.initializePluginInstancesForSessionsIfNeeded(
                            pluginId = pluginId,
                            sessions = targetSessions(pluginId, activeSessions),
                        )
                        if (requiresAgent(pluginId) && activatedSessionIds.isNotEmpty()) {
                            send(PluginReconciliationEvent.Activated(pluginId, activatedSessionIds))
                        }
                    }
                }
        }

        launch {
            enabledPluginsRepository.disabledPluginIdFlow.collect { pluginId ->
                if (requiresAgent(pluginId)) {
                    send(PluginReconciliationEvent.Deactivated(pluginId))
                }
                pluginInstanceService.unloadPluginInstancesForPlugin(pluginId)
            }
        }

        awaitClose { }
    }
}
