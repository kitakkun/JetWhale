package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.LoadedPluginInstance
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAppMetadata
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultPluginSessionReconciliationServiceTest {
    private val pluginId = "com.example.plugin"
    private val sessionId = "session-1"

    private val activeSession = DebugSession(
        id = sessionId,
        name = "test",
        isActive = true,
        transportSecurity = SessionTransportSecurity.LOOPBACK,
        installedPlugins = persistentListOf(JetWhalePluginInfo(pluginId, "1.0.0")),
    )

    private val loadedPlugin = LoadedHostPlugin(
        manifest = JetWhaleHostPluginManifest(
            pluginId = pluginId,
            pluginName = "Test",
            version = "1.0.0",
            factoryClass = "com.example.TestFactory",
        ),
        factory = object : JetWhaleHostPluginFactory {
            override fun createPlugin(): JetWhaleHostPlugin = object : JetWhaleHostPlugin() {}
        },
    )

    /**
     * A plugin installed while its id is already in the enabled set changes neither the enabled set
     * nor the session list, so loading it must be a reconciliation trigger of its own. Without it the
     * plugin never gets an instance and opening it fails until the app is restarted.
     */
    @Test
    fun `loading a plugin whose id is already enabled initializes its instances`() = runBlocking {
        val factoryRepository = FakePluginFactoryRepository()
        val instanceService = FakePluginInstanceService(factoryRepository)
        val service = DefaultPluginSessionReconciliationService(
            sessionRepository = FakeDebugSessionRepository(MutableStateFlow(persistentListOf(activeSession))),
            enabledPluginsRepository = FakeEnabledPluginsRepository(setOf(pluginId)),
            pluginFactoryRepository = factoryRepository,
            pluginInstanceService = instanceService,
        )

        val collectJob = launch { service.reconciliationEvents().collect { } }

        // The enabled id is reconciled from the start, but nothing is loaded yet, so no instance.
        assertEquals(emptySet(), withTimeout(TIMEOUT_MILLIS) { instanceService.calls.receive() })

        factoryRepository.load(loadedPlugin)

        assertEquals(setOf(sessionId), withTimeout(TIMEOUT_MILLIS) { instanceService.calls.receive() })

        collectJob.cancel()
    }

    private class FakeDebugSessionRepository(
        override val debugSessionsFlow: MutableStateFlow<ImmutableList<DebugSession>>,
    ) : DebugSessionRepository {
        override suspend fun registerDebugSession(
            sessionId: String,
            sessionName: String?,
            transportSecurity: SessionTransportSecurity,
            installedPlugins: List<JetWhalePluginInfo>,
            appMetadata: JetWhaleAppMetadata,
        ) = Unit

        override fun unregisterDebugSession(sessionId: String) = Unit
        override fun markAllSessionsInactive() = Unit
    }

    private class FakeEnabledPluginsRepository(enabled: Set<String>) : EnabledPluginsRepository {
        override val enabledPluginIdsFlow: MutableStateFlow<Set<String>> = MutableStateFlow(enabled)
        override val disabledPluginIdFlow: MutableSharedFlow<String> = MutableSharedFlow()

        override suspend fun setPluginEnabled(pluginId: String, enabled: Boolean) {
            enabledPluginIdsFlow.value = if (enabled) {
                enabledPluginIdsFlow.value + pluginId
            } else {
                enabledPluginIdsFlow.value - pluginId
            }
        }

        override suspend fun isPluginEnabled(pluginId: String): Boolean = pluginId in enabledPluginIdsFlow.value
    }

    private class FakePluginFactoryRepository : PluginFactoryRepository {
        private val plugins: MutableStateFlow<Map<String, LoadedHostPlugin>> = MutableStateFlow(emptyMap())
        override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>> = plugins
        override val loadedPlugins: Map<String, LoadedHostPlugin> get() = plugins.value
        override val failedJarsFlow: Flow<List<FailedPluginJar>> = MutableStateFlow(emptyList())

        fun load(plugin: LoadedHostPlugin) {
            plugins.value = plugins.value + (plugin.manifest.pluginId to plugin)
        }

        override suspend fun loadPlugin(pluginJarPath: String) = Unit
        override suspend fun unloadPlugin(pluginId: String) = Unit
        override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = emptyList()
        override suspend fun reloadPlugin(pluginJarPath: String): List<String> = emptyList()
        override fun tryRedefinePlugin(pluginJarPath: String): List<String> = emptyList()
    }

    private class FakePluginInstanceService(
        private val factoryRepository: PluginFactoryRepository,
    ) : PluginInstanceService {
        /** The session ids newly initialized by each reconciliation pass, in order. */
        val calls: Channel<Set<String>> = Channel(Channel.UNLIMITED)

        private val initializedSessionIds = mutableSetOf<String>()

        override val pluginInstanceEventFlow: SharedFlow<PluginInstanceEvent> = MutableSharedFlow()

        override fun initializePluginInstancesForSessionsIfNeeded(pluginId: String, sessionIds: Set<String>): Set<String> {
            // Mirrors the real service: an id with no loaded plugin yields no instance.
            val newSessionIds = if (factoryRepository.loadedPlugins[pluginId] == null) {
                emptySet()
            } else {
                sessionIds - initializedSessionIds
            }
            initializedSessionIds += newSessionIds
            calls.trySend(newSessionIds)
            return newSessionIds
        }

        override fun getLoadedPluginInstances(): List<LoadedPluginInstance> = emptyList()
        override fun unloadPluginInstanceForSession(sessionId: String) = Unit
        override fun getPluginInstanceForSession(pluginId: String, sessionId: String): JetWhaleHostPlugin? = null
        override fun unloadPluginInstancesForPlugin(pluginId: String) = Unit
        override fun clearAllPluginInstances() = Unit
        override suspend fun routeFrame(sessionId: String, frame: PluginFrame) = Unit
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
