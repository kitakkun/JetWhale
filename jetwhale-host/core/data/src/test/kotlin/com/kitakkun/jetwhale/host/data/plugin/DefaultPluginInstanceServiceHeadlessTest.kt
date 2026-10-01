package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginStorageService
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Whether a plugin renders a UI is only knowable from the instantiated plugin, so the service that
 * owns the instances is the one that has to report it.
 */
class DefaultPluginInstanceServiceHeadlessTest {
    private val pluginId = "com.example.plugin"
    private val sessionId = "session-1"

    private val storage = mock<JetWhalePluginStorage>()
    private val storageService = mock<PluginStorageService> {
        every { storageFor(any()) } returns storage
    }
    private val frameSender = mock<HostPluginFrameSender>()

    @Test
    fun `a plugin with no UI is reported as headless for the session it was created for`() {
        val service = serviceWith { object : JetWhaleHostPlugin() {} }

        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf(sessionId to null))

        assertEquals(mapOf(sessionId to setOf(pluginId)), service.headlessPluginsFlow.value.pluginIdsBySession)
    }

    @Test
    fun `a plugin that renders a UI is not reported as headless`() {
        val service = serviceWith { UiPlugin() }

        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf(sessionId to null))

        assertEquals(emptyMap(), service.headlessPluginsFlow.value.pluginIdsBySession)
    }

    @Test
    fun `unloading a session drops its headless entry`() {
        val service = serviceWith { object : JetWhaleHostPlugin() {} }
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf(sessionId to null))

        service.unloadPluginInstanceForSession(sessionId)

        assertEquals(emptyMap(), service.headlessPluginsFlow.value.pluginIdsBySession)
    }

    @Test
    fun `the server stopping disposes the apps' instances and keeps the host session's`() {
        val service = serviceWith { object : JetWhaleHostPlugin() {} }
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf(sessionId to null, HostSession.ID to null))

        service.clearAppSessionPluginInstances()

        assertEquals(null, service.getPluginInstanceForSession(pluginId, sessionId))
        assertNotNull(service.getPluginInstanceForSession(pluginId, HostSession.ID))
    }

    private fun serviceWith(createPlugin: () -> JetWhaleHostPlugin) = DefaultPluginInstanceService(
        pluginFactoryRepository = FakePluginFactoryRepository(
            LoadedHostPlugin(
                jarPath = "/plugins/plugin.jar",
                manifest = JetWhaleHostPluginManifest(
                    pluginId = pluginId,
                    pluginName = "Test",
                    version = "1.0.0",
                    factoryClass = "com.example.TestFactory",
                    requiresAgent = false,
                ),
                factory = object : JetWhaleHostPluginFactory {
                    override fun createPlugin(): JetWhaleHostPlugin = createPlugin()
                },
            ),
        ),
        frameSender = frameSender,
        pluginStorageService = storageService,
    )

    private class UiPlugin :
        JetWhaleHostPlugin(),
        JetWhaleHostPluginUi {
        @Composable
        override fun Content() = Unit
    }

    private class FakePluginFactoryRepository(plugin: LoadedHostPlugin) : PluginFactoryRepository {
        override val loadedPlugins: Map<String, LoadedHostPlugin> = mapOf(plugin.manifest.pluginId to plugin)
        override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>> = MutableStateFlow(loadedPlugins)
        override val loadedPluginVersions: Map<String, List<LoadedHostPlugin>> = mapOf(plugin.manifest.pluginId to listOf(plugin))
        override val loadedPluginVersionsFlow: Flow<Map<String, List<LoadedHostPlugin>>> = MutableStateFlow(loadedPluginVersions)
        override val failedJarsFlow: Flow<List<FailedPluginJar>> = MutableStateFlow(emptyList())

        override suspend fun loadPlugin(pluginJarPath: String, expectedSha256: String?) = Unit
        override suspend fun unloadPluginJar(pluginJarPath: String) = Unit
        override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = emptyList()
        override suspend fun reloadPlugin(pluginJarPath: String, expectedSha256: String?): List<String> = emptyList()
        override fun tryRedefinePlugin(pluginJarPath: String): List<String> = emptyList()
    }
}
