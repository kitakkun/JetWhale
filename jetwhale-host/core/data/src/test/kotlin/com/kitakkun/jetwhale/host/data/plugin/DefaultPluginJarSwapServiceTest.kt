package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.unit.Density
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.HeadlessPlugins
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.LoadedPluginInstance
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginFailures
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.PluginReconciliationEvent
import com.kitakkun.jetwhale.host.model.PluginSessionReconciliationService
import com.kitakkun.jetwhale.host.model.SafeMode
import com.kitakkun.jetwhale.host.model.SafeModeReason
import com.kitakkun.jetwhale.host.model.SafeModeService
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAppMetadata
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultPluginJarSwapServiceTest {
    private val pluginId = "com.example.plugin"
    private val sessionId = "session-1"
    private val jar: File = Files.createTempFile("plugin", ".jar").toFile()
    private val instances = RecordingPluginInstanceService()

    @AfterTest
    fun cleanUp() {
        jar.delete()
    }

    @Test
    fun `reloading a jar in safe mode creates no plugin instance`() = runBlocking {
        swapService(safeMode = SafeMode(SafeModeReason.RequestedOnCommandLine)).reload(jar.path, expectedSha256 = null)

        assertEquals(emptyList(), instances.initialized)
    }

    @Test
    fun `reloading a jar outside safe mode recreates the plugin's instances`() = runBlocking {
        swapService(safeMode = null).reload(jar.path, expectedSha256 = null)

        assertEquals(listOf(pluginId to setOf(sessionId)), instances.initialized)
    }

    private fun swapService(safeMode: SafeMode?) = DefaultPluginJarSwapService(
        pluginFactoryRepository = ReloadingFactoryRepository(pluginId),
        pluginInstanceService = instances,
        pluginComposeSceneService = NoSceneService,
        debugSessionRepository = OneSessionRepository(
            DebugSession(
                id = sessionId,
                name = "test",
                isActive = true,
                transportSecurity = SessionTransportSecurity.LOOPBACK,
                installedPlugins = persistentListOf(JetWhalePluginInfo(pluginId, "1.0.0")),
            ),
        ),
        enabledPluginsRepository = EnabledRepository(setOf(pluginId)),
        reconciliationService = AllSessionsReconciliation,
        safeModeService = FixedSafeModeService(safeMode),
    )

    private class ReloadingFactoryRepository(private val pluginId: String) : PluginFactoryRepository {
        override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>> = MutableStateFlow(emptyMap())
        override val loadedPlugins: Map<String, LoadedHostPlugin> = emptyMap()
        override val failedJarsFlow = MutableStateFlow(emptyList<FailedPluginJar>())
        override suspend fun loadPlugin(pluginJarPath: String, expectedSha256: String?) = Unit
        override suspend fun unloadPluginJar(pluginJarPath: String) = Unit
        override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = listOf(pluginId)
        override suspend fun reloadPlugin(pluginJarPath: String, expectedSha256: String?): List<String> = listOf(pluginId)
        override fun tryRedefinePlugin(pluginJarPath: String): List<String> = emptyList()
    }

    private class RecordingPluginInstanceService : PluginInstanceService {
        val initialized = mutableListOf<Pair<String, Set<String>>>()

        override val pluginFailuresFlow: StateFlow<PluginFailures> = MutableStateFlow(PluginFailures.Empty)
        override val pluginInstanceEventFlow: SharedFlow<PluginInstanceEvent> = MutableSharedFlow()
        override val headlessPluginsFlow: StateFlow<HeadlessPlugins> = MutableStateFlow(HeadlessPlugins.Empty)

        override fun initializePluginInstancesForSessionsIfNeeded(pluginId: String, sessionIds: Set<String>): Set<String> {
            initialized += pluginId to sessionIds
            return sessionIds
        }

        override fun getLoadedPluginInstances(): List<LoadedPluginInstance> = emptyList()
        override fun unloadPluginInstanceForSession(sessionId: String) = Unit
        override fun getPluginInstanceForSession(pluginId: String, sessionId: String): JetWhaleHostPlugin? = null
        override fun unloadPluginInstancesForPlugin(pluginId: String) = Unit
        override fun clearAppSessionPluginInstances() = Unit
        override suspend fun routeFrame(sessionId: String, frame: PluginFrame) = Unit
    }

    private object NoSceneService : PluginComposeSceneService {
        override fun updateHostDensity(density: Density) = Unit

        @OptIn(InternalComposeUiApi::class)
        override suspend fun getOrCreatePluginScene(pluginId: String, sessionId: String): PluginComposeScene = error("not used")

        override fun disposePluginSceneForSession(sessionId: String) = Unit
        override fun disposePluginScenesForPlugin(pluginId: String) = Unit
        override fun disposeAppSessionPluginScenes() = Unit
    }

    private class OneSessionRepository(session: DebugSession) : DebugSessionRepository {
        override val debugSessionsFlow: StateFlow<ImmutableList<DebugSession>> = MutableStateFlow(persistentListOf(session))

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

    private class EnabledRepository(enabled: Set<String>) : EnabledPluginsRepository {
        override val enabledPluginIdsFlow: MutableStateFlow<Set<String>> = MutableStateFlow(enabled)
        override val disabledPluginIdFlow: MutableSharedFlow<String> = MutableSharedFlow()
        override suspend fun setPluginEnabled(pluginId: String, enabled: Boolean) = Unit
        override suspend fun isPluginEnabled(pluginId: String): Boolean = pluginId in enabledPluginIdsFlow.value
    }

    private object AllSessionsReconciliation : PluginSessionReconciliationService {
        override fun requiresAgent(pluginId: String): Boolean = true
        override fun targetSessionIds(pluginId: String, sessions: List<DebugSession>): Set<String> = sessions.map(DebugSession::id).toSet()
        override fun reconciliationEvents(): Flow<PluginReconciliationEvent> = emptyFlow()
    }

    private class FixedSafeModeService(safeMode: SafeMode?) : SafeModeService {
        override val safeModeFlow: StateFlow<SafeMode?> = MutableStateFlow(safeMode)
        override fun leaveSafeMode() = Unit
    }
}
