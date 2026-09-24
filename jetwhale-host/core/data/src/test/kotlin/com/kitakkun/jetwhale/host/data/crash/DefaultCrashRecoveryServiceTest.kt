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

    private val markers = FakeRunMarkerRepository()
    private val plugins = FakePluginFactoryRepository()

    @AfterTest
    fun restore() {
        if (previousAppDataDir == null) System.clearProperty("jetwhale.appDataDir") else System.setProperty("jetwhale.appDataDir", previousAppDataDir)
        appDataDir.deleteRecursively()
    }

    @Test
    fun `a first start reports nothing and leaves a marker for this run`() {
        val service = newService()

        service.onStartup()

        assertNull(service.uncleanExitReportFlow.value)
        val marker = markers.markers.values.single()
        assertEquals(ProcessHandle.current().pid(), marker.pid)
        assertEquals(false, marker.startupCompleted)
    }

    @Test
    fun `a marker left by a dead process is reported as an unclean exit and then removed`() {
        markers.add(deadRunMarker(startupCompleted = true, consecutiveStartupCrashes = 0))
        val service = newService()

        service.onStartup()

        val report = assertNotNull(service.uncleanExitReportFlow.value)
        assertEquals(DEAD_PID, report.pid)
        assertEquals(false, report.duringStartup)
        assertEquals(0, service.consecutiveStartupCrashes)
        assertFalse(markers.markers.containsKey(DEAD_RUN_ID))
    }

    @Test
    fun `a marker of another host that is still running is neither reported nor touched`() {
        val otherHost = liveOtherHostMarker()
        markers.add(otherHost)
        val service = newService()

        service.onStartup()
        service.onCleanShutdown()

        assertNull(service.uncleanExitReportFlow.value)
        assertEquals(listOf(otherHost), markers.markers.values.toList())
    }

    @Test
    fun `a running process that started after the marker only reuses its pid`() {
        markers.add(liveOtherHostMarker().copy(startedAtMillis = 1_000))
        val service = newService()

        service.onStartup()

        assertNotNull(service.uncleanExitReportFlow.value)
    }

    @Test
    fun `a clean shutdown waits for a grace-period write under way and then removes the marker`() = runBlocking {
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CountDownLatch(1)
        val writeFinished = CountDownLatch(1)
        markers.beforeWrite = { marker ->
            if (marker.startupCompleted) {
                writeStarted.complete(Unit)
                releaseWrite.await()
            }
        }
        markers.afterWrite = { marker -> if (marker.startupCompleted) writeFinished.countDown() }
        val service = newService(startupGrace = Duration.ZERO)
        service.onStartup()
        withTimeout(TIMEOUT_MILLIS) { writeStarted.await() }

        val shutdown = Thread(service::onCleanShutdown).apply { start() }
        // Let the write finish only once the shutdown is committed: parked waiting for it, or past
        // the delete it must not run ahead of.
        while (shutdown.state !in setOf(Thread.State.WAITING, Thread.State.TIMED_WAITING) && markers.markers.size == 1) Thread.onSpinWait()
        releaseWrite.countDown()
        shutdown.join(TIMEOUT_MILLIS)
        writeFinished.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)

        assertEquals(emptyMap(), markers.markers)
    }

    @Test
    fun `a run that outlives the grace period is no longer a startup crash`() = runBlocking {
        markers.add(deadRunMarker(startupCompleted = false, consecutiveStartupCrashes = 0))
        val service = newService(startupGrace = Duration.ZERO)
        service.onStartup()

        val marker = withTimeout(TIMEOUT_MILLIS) { markers.state.map { it.values.singleOrNull() }.first { it?.startupCompleted == true } }

        assertEquals(0, marker?.consecutiveStartupCrashes)
    }

    @Test
    fun `startup crashes are counted until the count reaches safe mode`() {
        markers.add(deadRunMarker(startupCompleted = false, consecutiveStartupCrashes = 1))
        val service = newService()

        service.onStartup()

        assertEquals(2, service.consecutiveStartupCrashes)
        assertEquals(2, markers.markers.values.single().consecutiveStartupCrashes)
        assertEquals(SafeModeReason.RepeatedStartupCrashes, DefaultSafeModeService(SafeModeRequest(requested = false), service).safeModeFlow.value?.reason)
    }

    @Test
    fun `one startup crash does not start in safe mode`() {
        markers.add(deadRunMarker(startupCompleted = false, consecutiveStartupCrashes = 0))
        val service = newService()

        service.onStartup()

        assertNull(DefaultSafeModeService(SafeModeRequest(requested = false), service).safeModeFlow.value)
    }

    @Test
    fun `safe mode requested on the command line applies without any crash`() {
        val service = newService()
        service.onStartup()

        val safeMode = DefaultSafeModeService(SafeModeRequest(requested = true), service)

        assertEquals(SafeModeReason.RequestedOnCommandLine, safeMode.safeModeFlow.value?.reason)
        safeMode.leaveSafeMode()
        assertNull(safeMode.safeModeFlow.value)
    }

    @Test
    fun `the crash log is read from the logs directory and blamed on the plugin once it loads`() = runBlocking {
        File(appDataDir, "logs").mkdirs()
        val fixture = checkNotNull(javaClass.classLoader.getResource("crash/hs_err_skiko.log")).readText()
        File(appDataDir, "logs/hs_err_pid$DEAD_PID.log").writeText(fixture)
        markers.add(deadRunMarker(startupCompleted = true, consecutiveStartupCrashes = 0))
        val service = newService()

        service.onStartup()
        assertEquals("C  [libskiko-macos-arm64.dylib+0x1053d0]  SkBitmap::notifyPixelsChanged() const+0x0", service.uncleanExitReportFlow.value?.crashLog?.problematicFrame)

        plugins.load("com.kitakkun.jetwhale.mirror", "com.kitakkun.jetwhale.plugins.mirror.host.MirrorHostPluginFactory")

        val suspect = withTimeout(TIMEOUT_MILLIS) { service.uncleanExitReportFlow.filterNotNull().first { it.suspectedPlugin != null } }
        assertEquals("com.kitakkun.jetwhale.mirror", suspect.suspectedPlugin?.pluginId)

        plugins.load("com.kitakkun.jetwhale.mirror.headless", "com.kitakkun.jetwhale.plugins.mirror.host.MirrorHeadlessPluginFactory")

        val ambiguous = withTimeout(TIMEOUT_MILLIS) { service.uncleanExitReportFlow.filterNotNull().first { it.suspectedPlugin == null } }
        assertNull(ambiguous.suspectedPlugin)
    }

    @Test
    fun `dismissing clears the report`() {
        markers.add(deadRunMarker(startupCompleted = true, consecutiveStartupCrashes = 0))
        val service = newService()
        service.onStartup()

        service.dismissUncleanExitReport()

        assertNull(service.uncleanExitReportFlow.value)
    }

    private fun newService(startupGrace: Duration = 1.hours) = DefaultCrashRecoveryService(
        runMarkerRepository = markers,
        appDataDirectoryProvider = AppDataDirectoryProvider(AdditionalPluginDirectories(emptyList())),
        pluginFactoryRepository = plugins,
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
        val state = MutableStateFlow<Map<String, RunMarker>>(emptyMap())
        val markers: Map<String, RunMarker> get() = state.value
        var beforeWrite: (RunMarker) -> Unit = {}
        var afterWrite: (RunMarker) -> Unit = {}

        fun add(marker: RunMarker) {
            state.update { it + (marker.runId to marker) }
        }

        override fun readAll(): List<RunMarker> = markers.values.toList()

        override fun write(marker: RunMarker) {
            beforeWrite(marker)
            add(marker)
            afterWrite(marker)
        }

        override fun delete(runId: String) {
            state.update { it - runId }
        }
    }

    private class FakePluginFactoryRepository : PluginFactoryRepository {
        private val plugins = MutableStateFlow<Map<String, LoadedHostPlugin>>(emptyMap())
        override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>> = plugins
        override val loadedPlugins: Map<String, LoadedHostPlugin> get() = plugins.value
        override val failedJarsFlow: Flow<List<FailedPluginJar>> = MutableStateFlow(emptyList())

        fun load(pluginId: String, factoryClass: String) {
            val plugin = LoadedHostPlugin(
                manifest = JetWhaleHostPluginManifest(pluginId = pluginId, pluginName = pluginId, version = "1.0.0", factoryClass = factoryClass),
                factory = object : JetWhaleHostPluginFactory {
                    override fun createPlugin(): JetWhaleHostPlugin = object : JetWhaleHostPlugin() {}
                },
            )
            plugins.value = plugins.value + (pluginId to plugin)
        }

        override suspend fun loadPlugin(pluginJarPath: String) = Unit
        override suspend fun unloadPlugin(pluginId: String) = Unit
        override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = emptyList()
        override suspend fun reloadPlugin(pluginJarPath: String): List<String> = emptyList()
        override fun tryRedefinePlugin(pluginJarPath: String): List<String> = emptyList()
    }

    private companion object {
        // Far above any pid the OS hands out here, so no live process has it.
        const val DEAD_PID = 999_999_999L
        const val DEAD_RUN_ID = "dead-run"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
