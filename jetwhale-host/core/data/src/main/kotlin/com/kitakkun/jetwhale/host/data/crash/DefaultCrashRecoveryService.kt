package com.kitakkun.jetwhale.host.data.crash

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.CrashRecoveryService
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.RunMarker
import com.kitakkun.jetwhale.host.model.RunMarkerRepository
import com.kitakkun.jetwhale.host.model.SuspectedPlugin
import com.kitakkun.jetwhale.host.model.UncleanExitReport
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.time.Duration.Companion.seconds

/**
 * How long a run has to stay up before a crash no longer counts as a startup crash: long enough to
 * load every plugin and bring the servers up, short enough that a crash on first use of a screen is
 * not blamed on startup.
 */
private val STARTUP_GRACE = 30.seconds

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultCrashRecoveryService(
    private val runMarkerRepository: RunMarkerRepository,
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
    private val pluginFactoryRepository: PluginFactoryRepository,
) : CrashRecoveryService {
    private val logger = Logger.getLogger(DefaultCrashRecoveryService::class.java.name)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val uncleanExitReportFlow: StateFlow<UncleanExitReport?>
        field = MutableStateFlow(null)

    override var consecutiveStartupCrashes: Int = 0
        private set

    override fun onStartup() {
        val pid = ProcessHandle.current().pid()
        val previous = runMarkerRepository.read()
        // A marker whose process is still alive belongs to another host sharing this data directory,
        // not to a crashed one.
        if (previous != null && previous.pid != pid && !ProcessHandle.of(previous.pid).map(ProcessHandle::isAlive).orElse(false)) {
            reportUncleanExit(previous)
        }

        val marker = RunMarker(
            pid = pid,
            startedAtMillis = System.currentTimeMillis(),
            workingDirectory = System.getProperty("user.dir").orEmpty(),
            startupCompleted = false,
            consecutiveStartupCrashes = consecutiveStartupCrashes,
        )
        runMarkerRepository.write(marker)
        // Runs on every exit the JVM gets to finish — a closed window, Cmd+Q, SIGTERM — and on none
        // of the ones this is here to notice: a native crash or a kill leaves the marker behind.
        Runtime.getRuntime().addShutdownHook(Thread(::onCleanShutdown, "jetwhale-run-marker"))
        scope.launch {
            delay(STARTUP_GRACE)
            runMarkerRepository.write(marker.copy(startupCompleted = true, consecutiveStartupCrashes = 0))
        }
    }

    private fun onCleanShutdown() {
        runMarkerRepository.delete()
    }

    override fun dismissUncleanExitReport() {
        uncleanExitReportFlow.value = null
    }

    private fun reportUncleanExit(previous: RunMarker) {
        val duringStartup = !previous.startupCompleted
        consecutiveStartupCrashes = if (duringStartup) previous.consecutiveStartupCrashes + 1 else 0
        val crashLog = findJvmCrashLog(previous.pid, crashLogDirectories(previous))?.let { file ->
            try {
                parseJvmCrashLog(file.path, file.readText())
            } catch (e: IOException) {
                logger.log(Level.WARNING, "Could not read the crash log ${file.path}", e)
                null
            }
        }
        logger.warning("The previous run (pid ${previous.pid}) did not shut down cleanly; crash log: ${crashLog?.path ?: "none found"}")
        uncleanExitReportFlow.value = UncleanExitReport(
            pid = previous.pid,
            startedAtMillis = previous.startedAtMillis,
            duringStartup = duringStartup,
            crashLog = crashLog,
            suspectedPlugin = null,
            logsDirectory = appDataDirectoryProvider.getLogsDirectory().path,
        )
        if (crashLog == null) return
        // Plugins load after this runs, so the crash is attributed once their packages are known.
        scope.launch {
            pluginFactoryRepository.loadedPluginsFlow.collect { plugins ->
                val suspectId = crashLog.suspectPlugin(
                    plugins.mapValues { (_, plugin) -> plugin.manifest.factoryClass.substringBeforeLast('.', "") },
                ) ?: return@collect
                val suspect = SuspectedPlugin(pluginId = suspectId, pluginName = plugins.getValue(suspectId).manifest.pluginName)
                uncleanExitReportFlow.update { report -> report?.copy(suspectedPlugin = suspect) }
            }
        }
    }

    private fun crashLogDirectories(previous: RunMarker): List<File> = listOfNotNull(
        appDataDirectoryProvider.getLogsDirectory(),
        previous.workingDirectory.takeIf(String::isNotEmpty)?.let(::File),
        System.getProperty("java.io.tmpdir")?.let(::File),
        File("/tmp"),
        System.getProperty("user.home")?.let(::File),
    )
}
