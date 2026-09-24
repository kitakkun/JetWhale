package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.PluginReconciliationEvent
import com.kitakkun.jetwhale.host.model.PluginSessionReconciliationService
import com.kitakkun.jetwhale.host.model.SafeModeService
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
    private val safeModeService: SafeModeService,
) : PluginSessionReconciliationService {
    override fun requiresAgent(pluginId: String): Boolean = pluginFactoryRepository.loadedPlugins[pluginId]?.manifest?.requiresAgent ?: true

    override fun targetSessionIds(pluginId: String, sessions: List<DebugSession>): Set<String> = if (requiresAgent(pluginId)) {
        sessions
            .filter { session -> session.installedPlugins.any { it.pluginId == pluginId } }
            .map(DebugSession::id)
            .toSet()
    } else {
        setOf(HostSession.ID)
    }

    override fun reconciliationEvents(): Flow<PluginReconciliationEvent> = channelFlow {
        launch {
            combine(
                enabledPluginsRepository.enabledPluginIdsFlow,
                sessionRepository.debugSessionsFlow.map { sessions -> sessions.filter(DebugSession::isActive) },
                // Loading a plugin is a trigger of its own: the enabled set only grows, so
                // installing a jar whose pluginId is already enabled changes neither flow above,
                // and the plugin would never get an instance.
                pluginFactoryRepository.loadedPluginsFlow,
                // In safe mode no instance is created; leaving it re-runs this with the real set.
                safeModeService.safeModeFlow,
            ) { enabledPluginIds, activeSessions, _, safeMode ->
                (if (safeMode == null) enabledPluginIds else emptySet()) to activeSessions
            }
                .collect { (enabledPluginIds, activeSessions) ->
                    enabledPluginIds.forEach { pluginId ->
                        val activatedSessionIds = pluginInstanceService.initializePluginInstancesForSessionsIfNeeded(
                            pluginId = pluginId,
                            sessionIds = targetSessionIds(pluginId, activeSessions),
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
