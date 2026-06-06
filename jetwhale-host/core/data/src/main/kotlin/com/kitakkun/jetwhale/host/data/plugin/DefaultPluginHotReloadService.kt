package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.simulateHotReload
import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginHotReloadService
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.logging.Logger
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.name

/**
 * Watches the configured dev plugins directory and hot-reloads plugin jars on change.
 *
 * Reload sequence for a changed jar:
 * 1. dispose the affected plugin's running instances ([PluginInstanceService.unloadPluginInstancesForPlugin],
 *    which calls each instance's `onDispose`) and close its compose scenes,
 * 2. reload the factory from a fresh classloader (the old classloader is dropped — see
 *    [PluginFactoryRepository.reloadPlugin]),
 * 3. re-create instances for active sessions that have the plugin installed, and
 * 4. emit [pluginReloadedFlow] so the open plugin screen re-creates its scene from the new code.
 *
 * The whole feature is gated on [AppDataDirectoryProvider.getDevPluginsDir]; with no dev directory
 * configured, [start] returns immediately and nothing is watched.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPluginHotReloadService(
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val pluginInstanceService: PluginInstanceService,
    private val pluginComposeSceneService: PluginComposeSceneService,
    private val debugSessionRepository: DebugSessionRepository,
    private val enabledPluginsRepository: EnabledPluginsRepository,
) : PluginHotReloadService {
    private val logger = Logger.getLogger(DefaultPluginHotReloadService::class.java.name)
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private val mutablePluginReloadedFlow: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 16)
    override val pluginReloadedFlow: SharedFlow<String> = mutablePluginReloadedFlow.asSharedFlow()

    private var watchJob: Job? = null
    private var watchService: WatchService? = null

    override suspend fun start() {
        // Idempotent: a second start() must not spin up another WatchService/job and leak the first.
        if (watchJob != null) return
        val devDir = appDataDirectoryProvider.getDevPluginsDir() ?: return
        val devDirectory = File(devDir)
        if (!devDirectory.exists()) {
            devDirectory.mkdirs()
        }

        logger.info("Hot reload enabled. Watching dev plugins directory: $devDir")

        // Initial load of any jars already present in the dev directory.
        appDataDirectoryProvider.getDevPluginJarFilePaths().forEach { jarPath ->
            pluginFactoryRepository.loadPlugin(jarPath)
        }

        startWatching(Path(devDir))
    }

    private fun startWatching(devDirPath: Path) {
        val service = FileSystems.getDefault().newWatchService()
        watchService = service
        devDirPath.register(
            service,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
        )

        watchJob = coroutineScope.launch {
            while (isActive) {
                val key: WatchKey = try {
                    service.take()
                } catch (e: InterruptedException) {
                    break
                } catch (e: Throwable) {
                    // The watch service was closed while we were blocked in take().
                    break
                }

                val changedJarNames = key.pollEvents()
                    .mapNotNull { (it.context() as? Path)?.takeIf { path -> path.extension == "jar" }?.name }
                    .toSet()

                // A single build can fire several events (write + close); coalesce and let the file
                // settle before reloading to avoid reading a half-written jar.
                if (changedJarNames.isNotEmpty()) {
                    delay(DEBOUNCE_MILLIS)
                    // Drain events queued during the debounce window, keeping any *additional*
                    // changed jars so a second jar modified in the window is not dropped.
                    val allChangedJarNames = changedJarNames + drainPendingJarNames(service)
                    allChangedJarNames.forEach { jarName ->
                        reloadJar(devDirPath.resolve(jarName).toAbsolutePath().toString())
                    }
                }

                if (!key.reset()) {
                    // The watched directory is no longer accessible.
                    break
                }
            }
        }
    }

    /** Drains events queued during the debounce window, returning the names of any changed jars. */
    private fun drainPendingJarNames(service: WatchService): Set<String> {
        val names = mutableSetOf<String>()
        var pending: WatchKey? = service.poll()
        while (pending != null) {
            pending.pollEvents()
                .mapNotNullTo(names) { (it.context() as? Path)?.takeIf { path -> path.extension == "jar" }?.name }
            pending.reset()
            pending = service.poll()
        }
        return names
    }

    private suspend fun reloadJar(jarPath: String) {
        if (!File(jarPath).exists()) return

        // B1: try an in-place class redefinition first. On success the plugin's classloader and
        // instances (and therefore their state) are kept; we only recreate the compose scenes so the
        // redefined Content runs against the preserved instance state.
        val redefinedPluginId = pluginFactoryRepository.tryRedefinePlugin(jarPath)
        if (redefinedPluginId != null) {
            withContext(Dispatchers.Main) {
                if (PRESERVE_REMEMBER_VIA_SIMULATE_HOT_RELOAD) {
                    // B2: ask Compose to recompose every running composition in place using its own
                    // hot-reload mechanism, which preserves remember state. The plugin scene is not
                    // recreated, so composable-local state (scroll, text input) is kept too.
                    simulateHotReloadPreservingState()
                } else {
                    // B1: recreate only the compose scenes; the plugin instance (and its state) is
                    // kept, but composable-local remember is reset.
                    pluginComposeSceneService.disposePluginScenesForPlugin(redefinedPluginId)
                    mutablePluginReloadedFlow.emit(redefinedPluginId)
                }
            }
            logger.info("Hot reloaded plugin in place (state preserved): $redefinedPluginId")
            return
        }

        // Fallback: full reload via a fresh classloader (plugin instance state is lost).
        // Capture the plugin id currently served by this jar (if any) so that we can dispose its
        // running instances and scenes before swapping in the new code.
        val previousPluginId = pluginFactoryRepository.findPluginIdByJarPath(jarPath)
        previousPluginId?.let { disposePlugin(it) }

        val reloadedPluginId = pluginFactoryRepository.reloadPlugin(jarPath)
        if (reloadedPluginId == null) {
            logger.warning("Failed to reload plugin from $jarPath")
            return
        }

        // The plugin id may have changed if its manifest id changed across the rebuild; make sure the
        // previously loaded instances are gone in that case too.
        if (previousPluginId != null && previousPluginId != reloadedPluginId) {
            disposePlugin(previousPluginId)
        }

        reinitializeInstances(reloadedPluginId)

        logger.info("Hot reloaded plugin: $reloadedPluginId")
        mutablePluginReloadedFlow.emit(reloadedPluginId)
    }

    @OptIn(InternalComposeApi::class)
    private fun simulateHotReloadPreservingState() {
        // Compose's own hot-reload entry point: saves the state of all running compositions, disposes
        // them, and recomposes against the freshly redefined code — preserving remember state.
        simulateHotReload(Unit)
    }

    private fun disposePlugin(pluginId: String) {
        pluginInstanceService.unloadPluginInstancesForPlugin(pluginId)
        pluginComposeSceneService.disposePluginScenesForPlugin(pluginId)
    }

    /**
     * Recreates plugin instances for the active sessions that have the plugin installed, so that the
     * UI can immediately render the freshly loaded code. Only runs when the plugin is enabled.
     */
    private suspend fun reinitializeInstances(pluginId: String) {
        if (!enabledPluginsRepository.isPluginEnabled(pluginId)) return

        val activeSessionIds = debugSessionRepository.debugSessionsFlow.first()
            .filter { it.isActive }
            .filter { session -> session.installedPlugins.any { it.pluginId == pluginId } }
            .map { it.id }
            .toSet()

        if (activeSessionIds.isEmpty()) return

        // Instance creation drives compose, so do it on the main dispatcher to match the scene service.
        withContext(Dispatchers.Main) {
            pluginInstanceService.initializePluginInstancesForSessionsIfNeeded(
                pluginId = pluginId,
                sessionIds = activeSessionIds,
            )
        }
    }

    override fun stop() {
        watchJob?.cancel()
        watchJob = null
        watchService?.close()
        watchService = null
    }

    companion object {
        private const val DEBOUNCE_MILLIS = 300L

        // B2 vs B1. When true, preserve composable-local remember via Compose's in-place hot reload
        // (simulateHotReload); when false, recreate the plugin's compose scenes (instance state is
        // still preserved, but remember resets). B2 relies on @InternalComposeApi and must be
        // verified on the JetBrains Runtime — flip to false if a plugin's UI does not refresh.
        private const val PRESERVE_REMEMBER_VIA_SIMULATE_HOT_RELOAD = true
    }
}
