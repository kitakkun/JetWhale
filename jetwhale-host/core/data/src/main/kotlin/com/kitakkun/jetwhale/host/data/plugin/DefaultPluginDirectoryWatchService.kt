package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.PluginDirectoryWatchService
import com.kitakkun.jetwhale.host.model.PluginTrustService
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
import java.util.logging.Logger
import kotlin.time.Duration.Companion.seconds

/**
 * Polls the plugins directory rather than using a `WatchService`: telling a settled jar from one
 * still being written needs the directory's sizes and times on every tick anyway, and listing a
 * handful of jars once a second is cheap.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPluginDirectoryWatchService(
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
    private val pluginTrustService: PluginTrustService,
) : PluginDirectoryWatchService {
    private val logger = Logger.getLogger(DefaultPluginDirectoryWatchService::class.java.name)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var watchJob: Job? = null

    override fun start() {
        if (watchJob != null) return
        val poller = PluginJarDirectoryPoller(appDataDirectoryProvider.getPluginDirectory())
        watchJob = coroutineScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL)
                val changedJarPaths = poller.poll()
                if (changedJarPaths.isEmpty()) continue
                try {
                    pluginTrustService.onPluginJarsChanged(changedJarPaths)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One unreadable jar must not stop the host from noticing the next one.
                    logger.warning("Failed to handle changed plugin jars $changedJarPaths: ${e.message}")
                }
            }
        }
    }

    override fun stop() {
        watchJob?.cancel()
        watchJob = null
    }
}

private val POLL_INTERVAL = 1.seconds
