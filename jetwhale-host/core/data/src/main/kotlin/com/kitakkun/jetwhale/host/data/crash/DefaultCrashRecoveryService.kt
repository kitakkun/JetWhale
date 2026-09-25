package com.kitakkun.jetwhale.host.data.crash

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.CrashRecoveryService
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.RunMarker
import com.kitakkun.jetwhale.host.model.RunMarkerRepository
import com.kitakkun.jetwhale.host.model.StartupGrace
import com.kitakkun.jetwhale.host.model.SuspectedPlugin
import com.kitakkun.jetwhale.host.model.UncleanExitReport
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.time.Duration.Companion.seconds

@ContributesTo(AppScope::class)
interface StartupGraceProvider {
    /**
     * Long enough to load every plugin and bring the servers up, short enough that a crash on first
     * use of a screen is not blamed on startup.
     */
    @Provides
    fun provideStartupGrace(): StartupGrace = StartupGrace(30.seconds)
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultCrashRecoveryService(
    private val runMarkerRepository: RunMarkerRepository,
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val startupGrace: StartupGrace,
) : CrashRecoveryService {
    private val logger = Logger.getLogger(DefaultCrashRecoveryService::class.java.name)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Read by the shutdown hook, which runs on a thread of its own.
    @Volatile
    private var runMarker: RunMarker? = null

    @Volatile
    private var startupGraceJob: Job? = null

    override val uncleanExitReportFlow: StateFlow<UncleanExitReport?>
        field = MutableStateFlow(null)

    override var consecutiveStartupCrashes: Int = 0
        private set

    // Only the standalone host's main() calls this; the IntelliJ plugin, which bundles this class,
    // never does, so the shutdown hook below never outlives a plugin class loader.
    @Suppress("KOTRAIL_UNSCOPED_REGISTRATION_IN_UNLOADABLE_CODE")
    override fun onStartup() {
        // A marker whose process is still running belongs to another host sharing this data
        // directory; it is that host's to remove.
        val abandoned = runMarkerRepository.readAll().filterNot(::isStillRunning)
        abandoned.maxByOrNull(RunMarker::startedAtMillis)?.let(::reportUncleanExit)
        abandoned.forEach { runMarkerRepository.delete(it.runId) }

        val marker = RunMarker(
            runId = UUID.randomUUID().toString(),
            pid = ProcessHandle.current().pid(),
            startedAtMillis = System.currentTimeMillis(),
            workingDirectory = System.getProperty("user.dir").orEmpty(),
            startupCompleted = false,
            consecutiveStartupCrashes = consecutiveStartupCrashes,
        )
        runMarker = marker
        runMarkerRepository.write(marker)
        // Runs on every exit the JVM gets to finish — a closed window, Cmd+Q, SIGTERM — and on none
        // of the ones this is here to notice: a native crash or a kill leaves the marker behind.
        Runtime.getRuntime().addShutdownHook(Thread(::onCleanShutdown, "jetwhale-run-marker"))
        startupGraceJob = scope.launch {
            delay(startupGrace.duration)
            runMarkerRepository.write(marker.copy(startupCompleted = true, consecutiveStartupCrashes = 0))
        }
    }

    override fun onCleanShutdown() {
        // A grace-period write already under way would otherwise land after the delete and bring
        // the marker back; the write does not suspend, so cancelling alone cannot stop it.
        startupGraceJob?.let { job -> runBlocking { job.cancelAndJoin() } }
        runMarker?.let { runMarkerRepository.delete(it.runId) }
        scope.cancel()
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
        // Plugins load after this runs, so the crash is attributed once their packages are known,
        // and again whenever the set changes: a plugin loaded later may share the suspect's package.
        scope.launch {
            pluginFactoryRepository.loadedPluginsFlow.collect { plugins ->
                val suspect = crashLog.suspectPlugin(
                    plugins.mapValues { (_, plugin) -> plugin.manifest.factoryClass.substringBeforeLast('.', "") },
                )?.let { pluginId -> SuspectedPlugin(pluginId = pluginId, pluginName = plugins.getValue(pluginId).manifest.pluginName) }
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

/**
 * A process that started after the marker was written only reuses the pid; the run that wrote the
 * marker is gone.
 */
private fun isStillRunning(marker: RunMarker): Boolean = ProcessHandle.of(marker.pid)
    .filter(ProcessHandle::isAlive)
    .map { process -> process.info().startInstant().map { it.toEpochMilli() <= marker.startedAtMillis }.orElse(true) }
    .orElse(false)
