package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginDataStoreRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DefaultPluginInstanceServiceHostSessionTest {
    private val sentFrames = Channel<PluginFrame>(Channel.UNLIMITED)
    private val factoryRepository = SwappableFactoryRepository(loadedPlugin(RecordingPluginFactory()))
    private val service = DefaultPluginInstanceService(
        pluginFactoryRepository = factoryRepository,
        frameSender = object : HostPluginFrameSender {
            override suspend fun sendFrame(sessionId: String, frame: PluginFrame) {
                sentFrames.send(frame)
            }
        },
        pluginDataStoreRepository = mock<PluginDataStoreRepository> {
            every { storageFor(any()) } returns mock()
        },
    )

    @Test
    fun `an instance in the host session registers no handlers and never prepares, while an app session's does`() = runBlocking {
        service.initializePluginInstancesForSessionsIfNeeded(PLUGIN_ID, setOf(HostSession.ID, APP_SESSION_ID))
        service.startPluginInstancePreparation(PLUGIN_ID, HostSession.ID)
        service.startPluginInstancePreparation(PLUGIN_ID, APP_SESSION_ID)

        assertIs<PluginFrame.Request>(withTimeout(5.seconds) { sentFrames.receive() })
        assertNull(withTimeoutOrNull(300) { sentFrames.receive() })
        val hostInstance = instanceIn(HostSession.ID)
        val appInstance = instanceIn(APP_SESSION_ID)
        assertFalse(hostInstance.hasRegisteredHandlers)
        assertFalse(hostInstance.prepared)
        assertTrue(appInstance.hasRegisteredHandlers)
        service.unloadPluginInstancesForPlugin(PLUGIN_ID)
    }

    @Test
    fun `each instance learns from onCreate whether it runs in the host session`() {
        service.initializePluginInstancesForSessionsIfNeeded(PLUGIN_ID, setOf(HostSession.ID, APP_SESSION_ID))

        assertEquals(true, instanceIn(HostSession.ID).isInHostSessionAtCreate)
        assertEquals(false, instanceIn(APP_SESSION_ID).isInHostSessionAtCreate)
        service.unloadPluginInstancesForPlugin(PLUGIN_ID)
    }

    @Test
    fun `the messenger of an instance in the host session refuses, and an app session's works`() {
        service.initializePluginInstancesForSessionsIfNeeded(PLUGIN_ID, setOf(HostSession.ID, APP_SESSION_ID))

        val refusal = assertIs<IllegalStateException>(instanceIn(HostSession.ID).messengerFailureAtCreate)
        assertContains(refusal.message.orEmpty(), "no agent")
        assertNull(instanceIn(APP_SESSION_ID).messengerFailureAtCreate)
        service.unloadPluginInstancesForPlugin(PLUGIN_ID)
    }

    @Test
    fun `stopping the server keeps the host session's instance of a plugin that also runs in apps`() {
        service.initializePluginInstancesForSessionsIfNeeded(PLUGIN_ID, setOf(HostSession.ID, APP_SESSION_ID))

        service.clearAppSessionPluginInstances()

        assertNotNull(service.getPluginInstanceForSession(PLUGIN_ID, HostSession.ID))
        assertNull(service.getPluginInstanceForSession(PLUGIN_ID, APP_SESSION_ID))
        service.unloadPluginInstancesForPlugin(PLUGIN_ID)
    }

    @Test
    fun `a reload replaces the host session's instance along with the app sessions'`() {
        service.initializePluginInstancesForSessionsIfNeeded(PLUGIN_ID, setOf(HostSession.ID, APP_SESSION_ID))
        val reloadedFactory = RecordingPluginFactory()
        factoryRepository.loaded = loadedPlugin(reloadedFactory)

        val recreatedSessionIds = service.initializePluginInstancesForSessionsIfNeeded(PLUGIN_ID, setOf(HostSession.ID, APP_SESSION_ID))

        assertEquals(setOf(HostSession.ID, APP_SESSION_ID), recreatedSessionIds)
        assertTrue(instanceIn(HostSession.ID) in reloadedFactory.created)
        assertTrue(instanceIn(APP_SESSION_ID) in reloadedFactory.created)
        service.unloadPluginInstancesForPlugin(PLUGIN_ID)
    }

    private fun instanceIn(sessionId: String): RecordingPlugin = assertIs(service.getPluginInstanceForSession(PLUGIN_ID, sessionId))

    private fun loadedPlugin(factory: JetWhaleHostPluginFactory) = LoadedHostPlugin(
        manifest = JetWhaleHostPluginManifest(
            pluginId = PLUGIN_ID,
            pluginName = "Test",
            version = "1.0.0",
            factoryClass = "com.example.TestFactory",
            requiresAgent = true,
            runsInHostSession = true,
        ),
        factory = factory,
    )

    private class RecordingPluginFactory : JetWhaleHostPluginFactory {
        val created = mutableListOf<RecordingPlugin>()

        override fun createPlugin(): JetWhaleHostPlugin = RecordingPlugin().also(created::add)
    }

    private class RecordingPlugin : JetWhaleMessagingHostPlugin() {
        var hasRegisteredHandlers = false
        var prepared = false
        var isInHostSessionAtCreate: Boolean? = null
        var messengerFailureAtCreate: Throwable? = null

        override fun JetWhaleMessageHandlers.configure() {
            hasRegisteredHandlers = true
        }

        override fun onCreate() {
            isInHostSessionAtCreate = isInHostSession
            messengerFailureAtCreate = runCatching { messenger }.exceptionOrNull()
        }

        override suspend fun onPrepare() {
            prepared = true
            messenger.requestRaw(messageType = "com.example.plugin/prepare", payload = "{}", timeout = null)
        }
    }

    private class SwappableFactoryRepository(var loaded: LoadedHostPlugin) : PluginFactoryRepository {
        override val loadedPlugins: Map<String, LoadedHostPlugin> get() = mapOf(loaded.manifest.pluginId to loaded)
        override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>> = MutableStateFlow(loadedPlugins)
        override val failedJarsFlow: Flow<List<FailedPluginJar>> = MutableStateFlow(emptyList())

        override suspend fun loadPlugin(pluginJarPath: String, expectedSha256: String?) = Unit
        override suspend fun unloadPluginJar(pluginJarPath: String) = Unit
        override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = emptyList()
        override suspend fun reloadPlugin(pluginJarPath: String, expectedSha256: String?): List<String> = emptyList()
        override fun tryRedefinePlugin(pluginJarPath: String): List<String> = emptyList()
    }

    private companion object {
        const val PLUGIN_ID = "com.example.plugin"
        const val APP_SESSION_ID = "session-1"
    }
}
