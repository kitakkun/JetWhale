package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginDataStoreRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceState
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

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

    @Test
    fun `the server stopping disposes the apps' instances and keeps the host session's`() {
        val service = serviceWith { object : JetWhaleHostPlugin() {} }
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId, HostSession.ID))

        service.clearAppSessionPluginInstances()

        assertEquals(null, service.getPluginInstanceForSession(pluginId, sessionId))
        assertNotNull(service.getPluginInstanceForSession(pluginId, HostSession.ID))
    }

    @Test
    fun `waiting for an instance ends once the plugin is created for the session`() = runBlocking {
        val service = serviceWith { object : JetWhaleHostPlugin() {} }
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { service.pluginInstanceStateFlow(pluginId, sessionId).first { it != PluginInstanceState.Absent } }

        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))

        val state = withTimeout(5_000) { waiting.await() }
        assertSame(service.getPluginInstanceForSession(pluginId, sessionId), assertIs<PluginInstanceState.Running>(state).plugin)
    }

    @Test
    fun `the instance state turns absent when the instance is disposed`() = runBlocking {
        val service = serviceWith { object : JetWhaleHostPlugin() {} }
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))

        service.unloadPluginInstancesForPlugin(pluginId)

        assertEquals(PluginInstanceState.Absent, service.pluginInstanceStateFlow(pluginId, sessionId).first())
    }

    @Test
    fun `a publication read before another thread's change does not overwrite that change`() = runBlocking {
        val service = serviceWith { object : JetWhaleHostPlugin() {} }
        val firstPublicationHalfway = CountDownLatch(1)
        val secondChangeDone = CountDownLatch(1)
        // An unconfined collector runs inside the publishing thread's assignment, holding that thread
        // after it has read the instances and published the headless set, before it publishes the
        // instance states.
        val holdFirstPublication = launch(Dispatchers.Unconfined) {
            service.headlessPluginsFlow.first { it.pluginIdsBySession.isNotEmpty() }
            firstPublicationHalfway.countDown()
            // Bounded, because a publication that waits for the other one to finish never sees it.
            secondChangeDone.await(500, TimeUnit.MILLISECONDS)
        }

        val first = thread { service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId)) }
        firstPublicationHalfway.await()
        val second = thread {
            service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(HostSession.ID))
            secondChangeDone.countDown()
        }
        first.join()
        second.join()
        holdFirstPublication.join()

        val hostState = service.pluginInstanceStateFlow(pluginId, HostSession.ID).first()
        assertSame(service.getPluginInstanceForSession(pluginId, HostSession.ID), assertIs<PluginInstanceState.Running>(hostState).plugin)
    }

    @Test
    fun `a plugin whose creation throws is reported as failed to start`() = runBlocking {
        val service = serviceWith { error("factory broke") }

        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))

        val state = service.pluginInstanceStateFlow(pluginId, sessionId).first()
        assertEquals("factory broke", assertIs<PluginInstanceState.FailedToStart>(state).cause.message)
    }

    @Test
    fun `a failed start is forgotten once the plugin is unloaded`() = runBlocking {
        val service = serviceWith { error("factory broke") }
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))

        service.unloadPluginInstancesForPlugin(pluginId)

        assertEquals(PluginInstanceState.Absent, service.pluginInstanceStateFlow(pluginId, sessionId).first())
    }

    @Test
    fun `a disposed instance is published as gone before its onDispose runs`() = runBlocking {
        var stateDuringDispose: PluginInstanceState? = null
        lateinit var service: DefaultPluginInstanceService
        service = serviceWith {
            object : JetWhaleHostPlugin() {
                override fun onDispose() {
                    stateDuringDispose = runBlocking { service.pluginInstanceStateFlow(pluginId, sessionId).first() }
                }
            }
        }
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))

        service.unloadPluginInstanceForSession(sessionId)

        assertEquals(PluginInstanceState.Absent, stateDuringDispose)
    }

    private fun serviceWith(createPlugin: () -> JetWhaleHostPlugin) = DefaultPluginInstanceService(
        pluginFactoryRepository = FakePluginFactoryRepository(
            LoadedHostPlugin(
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
        pluginDataStoreRepository = dataStoreRepository,
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
        override val failedJarsFlow: Flow<List<FailedPluginJar>> = MutableStateFlow(emptyList())

        override suspend fun loadPlugin(pluginJarPath: String) = Unit
        override suspend fun unloadPlugin(pluginId: String) = Unit
        override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = emptyList()
        override suspend fun reloadPlugin(pluginJarPath: String): List<String> = emptyList()
        override fun tryRedefinePlugin(pluginJarPath: String): List<String> = emptyList()
    }
}
