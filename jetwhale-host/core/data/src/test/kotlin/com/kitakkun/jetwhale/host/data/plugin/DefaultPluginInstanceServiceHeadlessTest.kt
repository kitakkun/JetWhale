package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginDataStoreRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbResult
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginContext
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginScope
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import com.kitakkun.jetwhale.host.sdk.JetWhaleSessionInfo
import com.kitakkun.jetwhale.host.sdk.JetWhaleSessions
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Whether a plugin renders a UI is only knowable from the instantiated plugin, so the service that
 * owns the instances is the one that has to report it.
 */
class DefaultPluginInstanceServiceHeadlessTest {
    private val pluginId = "com.example.plugin"
    private val sessionId = "session-1"

    private val storage = mock<JetWhalePluginStorage>()
    private val dataStoreRepository = mock<PluginDataStoreRepository> {
        every { storageFor(any()) } returns storage
    }
    private val frameSender = mock<HostPluginFrameSender>()

    @Test
    fun `a plugin with no UI is reported as headless for the session it was created for`() {
        val service = serviceWith { object : JetWhaleHostPlugin() {} }

        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))

        assertEquals(mapOf(sessionId to setOf(pluginId)), service.headlessPluginsFlow.value.pluginIdsBySession)
    }

    @Test
    fun `a plugin that renders a UI is not reported as headless`() {
        val service = serviceWith { UiPlugin() }

        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))

        assertEquals(emptyMap(), service.headlessPluginsFlow.value.pluginIdsBySession)
    }

    @Test
    fun `unloading a session drops its headless entry`() {
        val service = serviceWith { object : JetWhaleHostPlugin() {} }
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))

        service.unloadPluginInstanceForSession(sessionId)

        assertEquals(emptyMap(), service.headlessPluginsFlow.value.pluginIdsBySession)
    }

    private fun serviceWith(
        scope: JetWhaleHostPluginScope = JetWhaleHostPluginScope.SESSION,
        createPlugin: () -> JetWhaleHostPlugin,
    ) = DefaultPluginInstanceService(
        pluginFactoryRepository = FakePluginFactoryRepository(
            LoadedHostPlugin(
                manifest = JetWhaleHostPluginManifest(
                    pluginId = pluginId,
                    pluginName = "Test",
                    version = "1.0.0",
                    factoryClass = "com.example.TestFactory",
                    requiresAgent = false,
                    scope = scope,
                ),
                factory = object : JetWhaleHostPluginFactory {
                    override fun createPlugin(context: JetWhaleHostPluginContext): JetWhaleHostPlugin = createPlugin()
                },
            ),
        ),
        frameSender = frameSender,
        pluginDataStoreRepository = dataStoreRepository,
        hostPluginContext = FakeHostPluginContext(),
    )

    // -- host-scoped instances -------------------------------------------------------------

    @Test
    fun `a host-scoped instance is created with no session connected`() {
        val service = serviceWith(scope = JetWhaleHostPluginScope.HOST) { object : JetWhaleHostPlugin() {} }

        val created = service.initializeHostScopedInstanceIfNeeded(pluginId)

        assertTrue(created)
        assertNotNull(service.getHostScopedInstance(pluginId))
        assertEquals(setOf(pluginId), service.headlessPluginsFlow.value.hostScopedPluginIds)
    }

    @Test
    fun `a host-scoped instance is created only once`() {
        val service = serviceWith(scope = JetWhaleHostPluginScope.HOST) { object : JetWhaleHostPlugin() {} }
        service.initializeHostScopedInstanceIfNeeded(pluginId)

        assertFalse(service.initializeHostScopedInstanceIfNeeded(pluginId))
    }

    @Test
    fun `closing a session leaves the host-scoped instance alone`() {
        val service = serviceWith(scope = JetWhaleHostPluginScope.HOST) { object : JetWhaleHostPlugin() {} }
        service.initializeHostScopedInstanceIfNeeded(pluginId)

        service.unloadPluginInstanceForSession(sessionId)

        assertNotNull(service.getHostScopedInstance(pluginId))
    }

    @Test
    fun `disabling the plugin disposes its host-scoped instance`() {
        val service = serviceWith(scope = JetWhaleHostPluginScope.HOST) { object : JetWhaleHostPlugin() {} }
        service.initializeHostScopedInstanceIfNeeded(pluginId)

        service.unloadPluginInstancesForPlugin(pluginId)

        assertNull(service.getHostScopedInstance(pluginId))
        assertTrue(service.headlessPluginsFlow.value.hostScopedPluginIds.isEmpty())
    }

    @Test
    fun `a messaging plugin declared host-scoped gets no instance`() {
        val service = serviceWith(scope = JetWhaleHostPluginScope.HOST) { object : JetWhaleMessagingHostPlugin() {} }

        val created = service.initializeHostScopedInstanceIfNeeded(pluginId)

        assertFalse(created)
        assertNull(service.getHostScopedInstance(pluginId))
    }

    private class FakeHostPluginContext : JetWhaleHostPluginContext {
        override val adb: JetWhaleAdb = object : JetWhaleAdb {
            override val executable: String = "adb"
            override suspend fun run(vararg args: String, timeout: Duration): JetWhaleAdbResult = JetWhaleAdbResult(0, "")
            override suspend fun <T> runStreaming(vararg args: String, timeout: Duration, consume: suspend (InputStream) -> T): T = consume(ByteArrayInputStream(ByteArray(0)))
        }
        override val sessions: JetWhaleSessions = object : JetWhaleSessions {
            override val active: StateFlow<List<JetWhaleSessionInfo>> = MutableStateFlow(emptyList())
        }
    }

    private class UiPlugin :
        JetWhaleHostPlugin(),
        JetWhaleHostPluginUi {
        @Composable
        override fun Content() = Unit
    }

    private class FakePluginFactoryRepository(plugin: LoadedHostPlugin) : PluginFactoryRepository {
        override val loadedPlugins: Map<String, LoadedHostPlugin> = mapOf(plugin.manifest.pluginId to plugin)
        override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>> = MutableStateFlow(loadedPlugins)
        override val failedJarsFlow: Flow<List<FailedPluginJar>> = MutableStateFlow(emptyList())

        override suspend fun loadPlugin(pluginJarPath: String) = Unit
        override suspend fun unloadPlugin(pluginId: String) = Unit
        override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = emptyList()
        override suspend fun reloadPlugin(pluginJarPath: String): List<String> = emptyList()
        override fun tryRedefinePlugin(pluginJarPath: String): List<String> = emptyList()
    }
}
