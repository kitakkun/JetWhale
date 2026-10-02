package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.PluginJarSwapService
import com.kitakkun.jetwhale.host.model.PluginSessionReconciliationService
import com.kitakkun.jetwhale.host.model.SafeModeService
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
    private val safeModeService: SafeModeService,
) : PluginJarSwapService {
    private val logger = Logger.getLogger(DefaultPluginJarSwapService::class.java.name)

    override val pluginReloadedFlow: SharedFlow<String>
        field = MutableSharedFlow<String>(extraBufferCapacity = 16)

    override suspend fun hotSwap(jarPath: String) {
        if (!File(jarPath).exists()) return

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
        if (expectedSha256 != null && withContext(Dispatchers.IO) { File(jarPath).sha256Hex() } != expectedSha256) {
            // Not the approved content: the repository refuses it (and records why) without touching
            // what is running, so the running instances are not disposed for nothing.
            pluginFactoryRepository.reloadPlugin(jarPath, expectedSha256)
            return
        }

        val takenOverPluginIds = withContext(Dispatchers.IO) { declaredPluginIds(File(jarPath)) }
            .filter { it in pluginFactoryRepository.loadedPlugins }
        val previousPluginIds = (pluginFactoryRepository.findPluginIdsByJarPath(jarPath) + takenOverPluginIds).distinct()
        previousPluginIds.forEach { disposePlugin(it) }

        val reloadedPluginIds = pluginFactoryRepository.reloadPlugin(jarPath, expectedSha256)
        if (reloadedPluginIds.isEmpty()) {
            logger.warning("Failed to reload plugin from $jarPath")
            // A failed reload leaves the previous code loaded, so restore the instances and scenes
            // disposed above.
            previousPluginIds.forEach {
                reinitializeInstances(it)
                pluginReloadedFlow.emit(it)
            }
            return
        }

        (previousPluginIds - reloadedPluginIds.toSet()).forEach { disposePlugin(it) }

        reloadedPluginIds.forEach { reinitializeInstances(it) }

        logger.info("Reloaded plugin(s): ${reloadedPluginIds.joinToString()}")
        reloadedPluginIds.forEach { pluginReloadedFlow.emit(it) }
    }

    override suspend fun remove(jarPath: String) {
        pluginFactoryRepository.findPluginIdsByJarPath(jarPath).forEach { disposePlugin(it) }
        pluginFactoryRepository.unloadPluginJar(jarPath)
    }

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
        // Leaving safe mode runs the reconciliation, which creates the instances then.
        if (safeModeService.safeModeFlow.value != null) return

        val activeSessions = debugSessionRepository.debugSessionsFlow.first().filter(DebugSession::isActive)
        val activeSessionIds = reconciliationService.targetSessionIds(pluginId, activeSessions)

        if (activeSessionIds.isEmpty()) return

        withContext(Dispatchers.Main) {
            pluginInstanceService.initializePluginInstancesForSessionsIfNeeded(
                pluginId = pluginId,
                sessionIds = activeSessionIds,
            )
        }
    }
}
