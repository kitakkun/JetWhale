package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginHotReloadService
import com.kitakkun.jetwhale.host.model.PluginJarSwapService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
 * Watches the configured dev plugins directory and hot-reloads plugin jars on change through
 * [PluginJarSwapService].
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
    private val pluginJarSwapService: PluginJarSwapService,
) : PluginHotReloadService {
    private val logger = Logger.getLogger(DefaultPluginHotReloadService::class.java.name)
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private var watchJob: Job? = null
    private var watchService: WatchService? = null

    override suspend fun start() {
        if (watchJob != null) return
        val devDir = appDataDirectoryProvider.getDevPluginsDir() ?: return
        val devDirectory = File(devDir)
        if (!devDirectory.exists()) {
            devDirectory.mkdirs()
        }

        logger.info("Hot reload enabled. Watching dev plugins directory: $devDir")

        // Dev jars load without approval: the dev directory is named by a JVM property at launch,
        // so whoever set it already chose what the host runs.
        appDataDirectoryProvider.getDevPluginJarFilePaths().forEach { jarPath ->
            pluginFactoryRepository.loadPlugin(jarPath, expectedSha256 = null)
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
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    break
                }

                val changedJarNames = key.changedJarNames()
                if (changedJarNames.isNotEmpty()) {
                    reloadChangedJars(devDirPath, service, changedJarNames)
                }

                if (!key.reset()) {
                    break
                }
            }
        }
    }

    /** The jars named by this key's pending events; non-jar entries are ignored. */
    private fun WatchKey.changedJarNames(): Set<String> = pollEvents()
        .mapNotNull { (it.context() as? Path)?.takeIf { path -> path.extension == "jar" }?.name }
        .toSet()

    /**
     * A single build can fire several events (write + close), so let the files settle before
     * reloading to avoid reading a half-written jar. Jars that changed during that window are
     * reloaded too, so a second jar modified in it is not dropped.
     */
    private suspend fun reloadChangedJars(devDirPath: Path, service: WatchService, changedJarNames: Set<String>) {
        delay(DEBOUNCE_MILLIS)
        (changedJarNames + drainPendingJarNames(service)).forEach { jarName ->
            pluginJarSwapService.hotSwap(devDirPath.resolve(jarName).toAbsolutePath().toString())
        }
    }

    /** Drains events queued during the debounce window, returning the names of any changed jars. */
    private fun drainPendingJarNames(service: WatchService): Set<String> {
        val names = mutableSetOf<String>()
        var pending: WatchKey? = service.poll()
        while (pending != null) {
            names += pending.changedJarNames()
            pending.reset()
            pending = service.poll()
        }
        return names
    }

    override fun stop() {
        watchJob?.cancel()
        watchJob = null
        // take() blocks its thread and ignores coroutine cancellation; closing the service is what
        // releases it.
        watchService?.close()
        watchService = null
    }

    companion object {
        private const val DEBOUNCE_MILLIS = 300L
    }
}
