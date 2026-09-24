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
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
        assertEquals(ProcessHandle.current().pid(), markers.marker?.pid)
        assertEquals(false, markers.marker?.startupCompleted)
    }

    @Test
    fun `a marker left by a dead process is reported as an unclean exit`() {
        markers.marker = deadRunMarker(startupCompleted = true, consecutiveStartupCrashes = 0)
        val service = newService()

        service.onStartup()

        val report = assertNotNull(service.uncleanExitReportFlow.value)
        assertEquals(DEAD_PID, report.pid)
        assertEquals(false, report.duringStartup)
        assertEquals(0, service.consecutiveStartupCrashes)
    }

    @Test
    fun `a marker whose process is still alive belongs to another host and is not a crash`() {
        markers.marker = deadRunMarker(startupCompleted = true, consecutiveStartupCrashes = 0).copy(pid = ProcessHandle.current().parent().get().pid())
        val service = newService()

        service.onStartup()

        assertNull(service.uncleanExitReportFlow.value)
    }

    @Test
    fun `startup crashes are counted until the count reaches safe mode`() {
        markers.marker = deadRunMarker(startupCompleted = false, consecutiveStartupCrashes = 1)
        val service = newService()

        service.onStartup()

        assertEquals(2, service.consecutiveStartupCrashes)
        assertEquals(2, markers.marker?.consecutiveStartupCrashes)
        assertEquals(SafeModeReason.RepeatedStartupCrashes, DefaultSafeModeService(SafeModeRequest(requested = false), service).safeModeFlow.value?.reason)
    }

    @Test
    fun `one startup crash does not start in safe mode`() {
        markers.marker = deadRunMarker(startupCompleted = false, consecutiveStartupCrashes = 0)
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
        markers.marker = deadRunMarker(startupCompleted = true, consecutiveStartupCrashes = 0)
        val service = newService()

        service.onStartup()
        assertEquals("C  [libskiko-macos-arm64.dylib+0x1053d0]  SkBitmap::notifyPixelsChanged() const+0x0", service.uncleanExitReportFlow.value?.crashLog?.problematicFrame)

        plugins.load("com.kitakkun.jetwhale.mirror", "com.kitakkun.jetwhale.plugins.mirror.host.MirrorHostPluginFactory")

        val suspect = withTimeout(TIMEOUT_MILLIS) { service.uncleanExitReportFlow.filterNotNull().first { it.suspectedPlugin != null } }
        assertEquals("com.kitakkun.jetwhale.mirror", suspect.suspectedPlugin?.pluginId)
    }

    @Test
    fun `dismissing clears the report`() {
        markers.marker = deadRunMarker(startupCompleted = true, consecutiveStartupCrashes = 0)
        val service = newService()
        service.onStartup()

        service.dismissUncleanExitReport()

        assertNull(service.uncleanExitReportFlow.value)
    }

    private fun newService() = DefaultCrashRecoveryService(
        runMarkerRepository = markers,
        appDataDirectoryProvider = AppDataDirectoryProvider(AdditionalPluginDirectories(emptyList())),
        pluginFactoryRepository = plugins,
    )

    private fun deadRunMarker(startupCompleted: Boolean, consecutiveStartupCrashes: Int) = RunMarker(
        pid = DEAD_PID,
        startedAtMillis = 1_000,
        workingDirectory = appDataDir.path,
        startupCompleted = startupCompleted,
        consecutiveStartupCrashes = consecutiveStartupCrashes,
    )

    private class FakeRunMarkerRepository : RunMarkerRepository {
        var marker: RunMarker? = null

        override fun read(): RunMarker? = marker

        override fun write(marker: RunMarker) {
            this.marker = marker
        }

        override fun delete() {
            marker = null
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
        const val TIMEOUT_MILLIS = 5_000L
    }
}
