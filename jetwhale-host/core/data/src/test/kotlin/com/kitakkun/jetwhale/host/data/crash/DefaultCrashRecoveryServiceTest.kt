package com.kitakkun.jetwhale.host.data.crash

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.AdditionalPluginDirectories
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.RunMarker
import com.kitakkun.jetwhale.host.model.RunMarkerRepository
import com.kitakkun.jetwhale.host.model.SafeModeReason
import com.kitakkun.jetwhale.host.model.SafeModeRequest
import com.kitakkun.jetwhale.host.model.StartupGrace
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class DefaultCrashRecoveryServiceTest {
    private val appDataDir: File = Files.createTempDirectory("jetwhale-app-data").toFile()
    private val previousAppDataDir = System.getProperty("jetwhale.appDataDir")

    init {
        System.setProperty("jetwhale.appDataDir", appDataDir.path)
    }

    private val runMarkerRepository = FakeRunMarkerRepository()
    private val pluginFactoryRepository = FakePluginFactoryRepository()

    @AfterTest
    fun restoreAppDataDirPropertyAndDeleteAppDataDir() {
        if (previousAppDataDir == null) System.clearProperty("jetwhale.appDataDir") else System.setProperty("jetwhale.appDataDir", previousAppDataDir)
        appDataDir.deleteRecursively()
    }

    @Test
    fun `a first start reports nothing and leaves a marker for this run`() {
        val service = newService()

        service.reportUncleanExitAndWriteRunMarker()

        assertNull(service.uncleanExitReportFlow.value)
        val marker = runMarkerRepository.markers.values.single()
        assertEquals(ProcessHandle.current().pid(), marker.pid)
        assertEquals(false, marker.startupCompleted)
    }

    @Test
    fun `a marker left by a dead process is reported as an unclean exit and then removed`() {
        runMarkerRepository.add(deadRunMarker(startupCompleted = true, consecutiveStartupCrashes = 0))
        val service = newService()

        service.reportUncleanExitAndWriteRunMarker()

        assertNotNull(service.uncleanExitReportFlow.value)
        assertEquals(0, service.consecutiveStartupCrashes)
        assertFalse(runMarkerRepository.markers.containsKey(DEAD_RUN_ID))
    }

    @Test
    fun `a marker of another host that is still running is neither reported nor touched`() {
        val otherHostMarker = liveOtherHostMarker()
        runMarkerRepository.add(otherHostMarker)
        val service = newService()

        service.reportUncleanExitAndWriteRunMarker()
        service.deleteRunMarker()

        assertNull(service.uncleanExitReportFlow.value)
        assertEquals(listOf(otherHostMarker), runMarkerRepository.markers.values.toList())
    }

    @Test
    fun `a running process that started after the marker only reuses its pid`() {
        runMarkerRepository.add(liveOtherHostMarker().copy(startedAtMillis = 1_000))
        val service = newService()

        service.reportUncleanExitAndWriteRunMarker()

        assertNotNull(service.uncleanExitReportFlow.value)
    }

    @Test
    fun `a marker left by an earlier run that had this run's pid is still reported`() {
        runMarkerRepository.add(deadRunMarker(startupCompleted = true, consecutiveStartupCrashes = 0).copy(pid = ProcessHandle.current().pid()))
        val service = newService()

        service.reportUncleanExitAndWriteRunMarker()

        assertNotNull(service.uncleanExitReportFlow.value)
    }

    @Test
    fun `a clean shutdown waits for a grace-period write under way and then removes the marker`() = runBlocking {
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CountDownLatch(1)
        val writeFinished = CountDownLatch(1)
        runMarkerRepository.beforeWrite = { marker ->
            if (marker.startupCompleted) {
                writeStarted.complete(Unit)
                releaseWrite.await()
            }
        }
        runMarkerRepository.afterWrite = { marker -> if (marker.startupCompleted) writeFinished.countDown() }
        val service = newService(startupGrace = Duration.ZERO)
        service.reportUncleanExitAndWriteRunMarker()
        withTimeout(TIMEOUT_MILLIS) { writeStarted.await() }

        val cleanShutdownThread = Thread(service::deleteRunMarker).apply { start() }
        // Let the write finish only once the shutdown is committed: parked waiting for it, or past
        // the delete it must not run ahead of.
        while (cleanShutdownThread.state !in setOf(Thread.State.WAITING, Thread.State.TIMED_WAITING) && runMarkerRepository.markers.size == 1) Thread.onSpinWait()
        releaseWrite.countDown()
        cleanShutdownThread.join(TIMEOUT_MILLIS)
        writeFinished.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)

        assertEquals(emptyMap(), runMarkerRepository.markers)
    }

    @Test
    fun `a run that outlives the grace period is no longer a startup crash`() = runBlocking {
        runMarkerRepository.add(deadRunMarker(startupCompleted = false, consecutiveStartupCrashes = 0))
        val service = newService(startupGrace = Duration.ZERO)
        service.reportUncleanExitAndWriteRunMarker()

        val marker = withTimeout(TIMEOUT_MILLIS) { runMarkerRepository.markersFlow.map { it.values.singleOrNull() }.first { it?.startupCompleted == true } }

        assertEquals(0, marker?.consecutiveStartupCrashes)
    }

    @Test
    fun `startup crashes are counted until the count reaches safe mode`() {
        runMarkerRepository.add(deadRunMarker(startupCompleted = false, consecutiveStartupCrashes = 1))
        val service = newService()

        service.reportUncleanExitAndWriteRunMarker()

        assertEquals(2, service.consecutiveStartupCrashes)
        assertEquals(2, runMarkerRepository.markers.values.single().consecutiveStartupCrashes)
        assertEquals(SafeModeReason.RepeatedStartupCrashes, DefaultSafeModeService(SafeModeRequest(requested = false), service).safeModeFlow.value?.reason)
    }

    @Test
    fun `one startup crash does not start in safe mode`() {
        runMarkerRepository.add(deadRunMarker(startupCompleted = false, consecutiveStartupCrashes = 0))
        val service = newService()

        service.reportUncleanExitAndWriteRunMarker()

        assertNull(DefaultSafeModeService(SafeModeRequest(requested = false), service).safeModeFlow.value)
    }

    @Test
    fun `safe mode requested on the command line applies without any crash`() {
        val service = newService()
        service.reportUncleanExitAndWriteRunMarker()

        val safeModeService = DefaultSafeModeService(SafeModeRequest(requested = true), service)

        assertEquals(SafeModeReason.RequestedOnCommandLine, safeModeService.safeModeFlow.value?.reason)
        safeModeService.leaveSafeMode()
        assertNull(safeModeService.safeModeFlow.value)
    }

    @Test
    fun `the crash log is read from the logs directory and blamed on the plugin once it loads`() = runBlocking {
        File(appDataDir, "logs").mkdirs()
        val fixture = checkNotNull(javaClass.classLoader.getResource("crash/hs_err_skiko.log")).readText()
        val crashLogFile = File(appDataDir, "logs/hs_err_pid$DEAD_PID.log").apply { writeText(fixture) }
        runMarkerRepository.add(deadRunMarker(startupCompleted = true, consecutiveStartupCrashes = 0))
        val service = newService()

        service.reportUncleanExitAndWriteRunMarker()
        assertEquals(crashLogFile.path, service.uncleanExitReportFlow.value?.crashLog?.path)

        pluginFactoryRepository.addLoadedPlugin("com.kitakkun.jetwhale.mirror", "com.kitakkun.jetwhale.plugins.mirror.host.MirrorHostPluginFactory")

        val attributedReport = withTimeout(TIMEOUT_MILLIS) { service.uncleanExitReportFlow.filterNotNull().first { it.suspectedPlugin != null } }
        assertEquals("com.kitakkun.jetwhale.mirror", attributedReport.suspectedPlugin?.pluginId)

        pluginFactoryRepository.addLoadedPlugin("com.kitakkun.jetwhale.mirror.headless", "com.kitakkun.jetwhale.plugins.mirror.host.MirrorHeadlessPluginFactory")

        val unattributedReport = withTimeout(TIMEOUT_MILLIS) { service.uncleanExitReportFlow.filterNotNull().first { it.suspectedPlugin == null } }
        assertNull(unattributedReport.suspectedPlugin)
    }

    @Test
    fun `dismissing clears the report`() {
        runMarkerRepository.add(deadRunMarker(startupCompleted = true, consecutiveStartupCrashes = 0))
        val service = newService()
        service.reportUncleanExitAndWriteRunMarker()

        service.dismissUncleanExitReport()

        assertNull(service.uncleanExitReportFlow.value)
    }

    private fun newService(startupGrace: Duration = 1.hours) = DefaultCrashRecoveryService(
        runMarkerRepository = runMarkerRepository,
        appDataDirectoryProvider = AppDataDirectoryProvider(AdditionalPluginDirectories(emptyList())),
        pluginFactoryRepository = pluginFactoryRepository,
        startupGrace = StartupGrace(startupGrace),
    )

    private fun liveOtherHostMarker() = RunMarker(
        runId = "other-host",
        pid = ProcessHandle.current().parent().get().pid(),
        startedAtMillis = System.currentTimeMillis(),
        workingDirectory = appDataDir.path,
        startupCompleted = true,
        consecutiveStartupCrashes = 0,
    )

    private fun deadRunMarker(startupCompleted: Boolean, consecutiveStartupCrashes: Int) = RunMarker(
        runId = DEAD_RUN_ID,
        pid = DEAD_PID,
        startedAtMillis = 1_000,
        workingDirectory = appDataDir.path,
        startupCompleted = startupCompleted,
        consecutiveStartupCrashes = consecutiveStartupCrashes,
    )

    private class FakeRunMarkerRepository : RunMarkerRepository {
        val markersFlow = MutableStateFlow<Map<String, RunMarker>>(emptyMap())
        val markers: Map<String, RunMarker> get() = markersFlow.value
        var beforeWrite: (RunMarker) -> Unit = {}
        var afterWrite: (RunMarker) -> Unit = {}

        fun add(marker: RunMarker) {
            markersFlow.update { it + (marker.runId to marker) }
        }

        override fun readAll(): List<RunMarker> = markers.values.toList()

        override fun write(marker: RunMarker) {
            beforeWrite(marker)
            add(marker)
            afterWrite(marker)
        }

        override fun delete(runId: String) {
            markersFlow.update { it - runId }
        }
    }

    private class FakePluginFactoryRepository : PluginFactoryRepository {
        override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>>
            field = MutableStateFlow<Map<String, LoadedHostPlugin>>(emptyMap())
        override val loadedPlugins: Map<String, LoadedHostPlugin> get() = loadedPluginsFlow.value
        override val failedJarsFlow: Flow<List<FailedPluginJar>> = MutableStateFlow(emptyList())

        fun addLoadedPlugin(pluginId: String, factoryClass: String) {
            val plugin = LoadedHostPlugin(
                manifest = JetWhaleHostPluginManifest(pluginId = pluginId, pluginName = pluginId, version = "1.0.0", factoryClass = factoryClass),
                factory = object : JetWhaleHostPluginFactory {
                    override fun createPlugin(): JetWhaleHostPlugin = object : JetWhaleHostPlugin() {}
                },
            )
            loadedPluginsFlow.value = loadedPluginsFlow.value + (pluginId to plugin)
        }

        override suspend fun loadPlugin(pluginJarPath: String, expectedSha256: String?) = Unit
        override suspend fun unloadPluginJar(pluginJarPath: String) = Unit
        override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = emptyList()
        override suspend fun reloadPlugin(pluginJarPath: String, expectedSha256: String?): List<String> = emptyList()
        override fun tryRedefinePlugin(pluginJarPath: String): List<String> = emptyList()
    }

    private companion object {
        const val DEAD_PID = 999_999_999L
        const val DEAD_RUN_ID = "dead-run"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
