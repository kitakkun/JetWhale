package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
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

    override fun targetSessionIds(pluginId: String, sessions: List<DebugSession>): Set<String> = if (requiresAgent(pluginId)) {
        sessions
            .filter { session -> session.installedPlugins.any { it.pluginId == pluginId } }
            .map { it.id }
            .toSet()
    } else {
        sessions.map { it.id }.toSet()
    }

    override fun reconciliationEvents(): Flow<PluginReconciliationEvent> = channelFlow {
        // Enable/session reconciliation: whenever the enabled set or the active sessions change,
        // (re)initialize instances for the sessions each enabled plugin should target. Instances are
        // also (re)initialized as sessions come and go because this reacts to the session flow too.
        launch {
            combine(
                enabledPluginsRepository.enabledPluginIdsFlow,
                sessionRepository.debugSessionsFlow.map { sessions -> sessions.filter { it.isActive } },
                // Loading a plugin is a reconciliation trigger in its own right. The enabled set only
                // ever grows (nothing removes an id when a jar is deleted or its trust revoked), so
                // installing a jar whose pluginId is already enabled changes neither of the flows
                // above — without this the freshly loaded plugin would never get an instance, and
                // opening it would fail until the next enable toggle, session, or app restart.
                pluginFactoryRepository.loadedPluginsFlow,
            ) { enabledPluginIds, activeSessions, _ -> enabledPluginIds to activeSessions }
                .collect { (enabledPluginIds, activeSessions) ->
                    enabledPluginIds.forEach { pluginId ->
                        val activatedSessionIds = pluginInstanceService.initializePluginInstancesForSessionsIfNeeded(
                            pluginId = pluginId,
                            sessionIds = targetSessionIds(pluginId, activeSessions),
                        )
                        // Only newly-initialized sessions are notified (some may already have the
                        // instance from an earlier reconciliation), and only for agent-backed plugins
                        // (host-only plugins have no agent to activate).
                        if (requiresAgent(pluginId) && activatedSessionIds.isNotEmpty()) {
                            send(PluginReconciliationEvent.Activated(pluginId, activatedSessionIds))
                        }
                    }
                }
        }

        // Disable reconciliation: unload the plugin's instances everywhere and tell the agents.
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
