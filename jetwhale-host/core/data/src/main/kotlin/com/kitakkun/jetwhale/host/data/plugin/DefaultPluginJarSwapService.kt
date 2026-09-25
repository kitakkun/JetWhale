package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.PluginJarSwapService
import com.kitakkun.jetwhale.host.model.PluginSessionReconciliationService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.logging.Logger

/**
 * Reload sequence for a changed jar:
 * 1. dispose the affected plugin's running instances ([PluginInstanceService.unloadPluginInstancesForPlugin],
 *    which calls each instance's `onDispose`) and close its compose scenes,
 * 2. reload the factory from a fresh classloader (the old classloader is dropped — see
 *    [PluginFactoryRepository.reloadPlugin]),
 * 3. re-create instances for active sessions that have the plugin installed, and
 * 4. emit [pluginReloadedFlow] so the open plugin screen re-creates its scene from the new code.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPluginJarSwapService(
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val pluginInstanceService: PluginInstanceService,
    private val pluginComposeSceneService: PluginComposeSceneService,
    private val debugSessionRepository: DebugSessionRepository,
    private val enabledPluginsRepository: EnabledPluginsRepository,
    private val reconciliationService: PluginSessionReconciliationService,
) : PluginJarSwapService {
    private val logger = Logger.getLogger(DefaultPluginJarSwapService::class.java.name)

    override val pluginReloadedFlow: SharedFlow<String>
        field = MutableSharedFlow<String>(extraBufferCapacity = 16)

    override suspend fun hotSwap(jarPath: String) {
        if (!File(jarPath).exists()) return

        // Try an in-place class redefinition first: it keeps the plugins' classloader and instances
        // (so instance state survives) and recreates only their compose scenes, so the redefined
        // Content runs against that state. Composable-local `remember` is reset with the scene, so
        // state that must survive a reload belongs in the plugin instance. Unlike Compose Hot
        // Reload, this reaches the classes in the plugins' child classloader.
        val redefinedPluginIds = pluginFactoryRepository.tryRedefinePlugin(jarPath)
        if (redefinedPluginIds.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                redefinedPluginIds.forEach(pluginComposeSceneService::disposePluginScenesForPlugin)
            }
            logger.info("Hot reloaded plugin(s) in place (instance state preserved): ${redefinedPluginIds.joinToString()}")
            redefinedPluginIds.forEach { pluginReloadedFlow.emit(it) }
            return
        }
        reload(jarPath, expectedSha256 = null)
    }

    override suspend fun reload(jarPath: String, expectedSha256: String?) {
        if (!File(jarPath).exists()) return

        // Plugin instance state is lost. Capture the plugin ids currently served by this jar so that
        // we can dispose their running instances and scenes before swapping in the new code.
        val previousPluginIds = pluginFactoryRepository.findPluginIdsByJarPath(jarPath)
        previousPluginIds.forEach { disposePlugin(it) }

        val reloadedPluginIds = pluginFactoryRepository.reloadPlugin(jarPath, expectedSha256)
        if (reloadedPluginIds.isEmpty()) {
            logger.warning("Failed to reload plugin from $jarPath")
            // A failed reload (e.g. a compile error in the rebuilt jar) leaves the previously loaded
            // code intact in the repository, so restore the instances/scenes we disposed above instead
            // of leaving active sessions without the plugin until the next successful build.
            previousPluginIds.forEach {
                reinitializeInstances(it)
                pluginReloadedFlow.emit(it)
            }
            return
        }

        // Some plugin ids may have disappeared if the jar's manifest changed across the rebuild
        // (a plugin removed/renamed); make sure their previously loaded instances are gone too.
        (previousPluginIds - reloadedPluginIds.toSet()).forEach { disposePlugin(it) }

        reloadedPluginIds.forEach { reinitializeInstances(it) }

        logger.info("Reloaded plugin(s): ${reloadedPluginIds.joinToString()}")
        reloadedPluginIds.forEach { pluginReloadedFlow.emit(it) }
    }

    override suspend fun remove(jarPath: String) {
        pluginFactoryRepository.findPluginIdsByJarPath(jarPath).forEach { disposePlugin(it) }
        pluginFactoryRepository.unloadPluginJar(jarPath)
    }

    // The scene service keeps its scenes on the main thread; the directory watchers call in from IO.
    private suspend fun disposePlugin(pluginId: String) = withContext(Dispatchers.Main) {
        pluginInstanceService.unloadPluginInstancesForPlugin(pluginId)
        pluginComposeSceneService.disposePluginScenesForPlugin(pluginId)
    }

    /**
     * Recreates plugin instances for the active sessions that have the plugin installed, so that the
     * UI can immediately render the freshly loaded code. Only runs when the plugin is enabled.
     */
    private suspend fun reinitializeInstances(pluginId: String) {
        if (!enabledPluginsRepository.isPluginEnabled(pluginId)) return

        // The target-session rule (host-only vs agent-backed) lives in the reconciliation service.
        val activeSessions = debugSessionRepository.debugSessionsFlow.first().filter(DebugSession::isActive)
        val activeSessionIds = reconciliationService.targetSessionIds(pluginId, activeSessions)

        if (activeSessionIds.isEmpty()) return

        // Instance creation drives compose, so do it on the main dispatcher to match the scene service.
        withContext(Dispatchers.Main) {
            pluginInstanceService.initializePluginInstancesForSessionsIfNeeded(
                pluginId = pluginId,
                sessionIds = activeSessionIds,
            )
        }
    }
}
