package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginDataStoreRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginScreenState
import com.kitakkun.jetwhale.host.model.PluginSessionReconciliationService
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Swapping a plugin's jar replaces its instances with ones built from the new classloader, and an
 * open screen has to follow them instead of rendering a scene whose code is gone.
 */
class DefaultPluginJarSwapServiceTest {
    private val pluginId = "com.example.plugin"
    private val jar = File.createTempFile("plugin", ".jar")
    private val sceneFactory = RecordingPluginComposeSceneFactory()
    private val repository = SwappingPluginFactoryRepository(loadedPlugin())
    private val instanceService = DefaultPluginInstanceService(
        pluginFactoryRepository = repository,
        frameSender = mock<HostPluginFrameSender>(),
        pluginDataStoreRepository = mock<PluginDataStoreRepository> {
            every { storageFor(any()) } returns mock<JetWhalePluginStorage>()
        },
        pluginComposeSceneFactory = sceneFactory,
    )
    private val swapService = DefaultPluginJarSwapService(
        pluginFactoryRepository = repository,
        pluginInstanceService = instanceService,
        debugSessionRepository = mock<DebugSessionRepository> {
            every { debugSessionsFlow } returns MutableStateFlow(persistentListOf())
        },
        enabledPluginsRepository = mock<EnabledPluginsRepository> {
            everySuspend { isPluginEnabled(any()) } returns true
        },
        reconciliationService = mock<PluginSessionReconciliationService> {
            every { targetSessionIds(any(), any()) } returns setOf(HostSession.ID)
        },
    )

    @AfterTest
    fun deleteJar() {
        jar.delete()
    }

    @Test
    fun `an open screen follows the new instance of a swapped jar and the old scene is closed`() = runBlocking<Unit> {
        instanceService.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(HostSession.ID))
        val states = Channel<PluginScreenState>(Channel.UNLIMITED)
        val screen = launch { instanceService.pluginScreenStateFlow(pluginId, HostSession.ID).collect(states::send) }
        val oldScene = assertIs<PluginScreenState.Ready>(withTimeout(TIMEOUT_MILLIS) { states.receive() }).scene

        swapService.reload(jar.path, expectedSha256 = null)

        withTimeout(TIMEOUT_MILLIS) {
            states.receiveAsFlow().filterIsInstance<PluginScreenState.Ready>().first { it.scene !== oldScene }
        }
        withTimeout(TIMEOUT_MILLIS) { sceneFactory.closed.first { oldScene in it } }
        screen.cancel()
    }

    private fun loadedPlugin() = LoadedHostPlugin(
        manifest = JetWhaleHostPluginManifest(
            pluginId = pluginId,
            pluginName = "Test",
            version = "1.0.0",
            factoryClass = "com.example.TestFactory",
            requiresAgent = false,
        ),
        factory = object : JetWhaleHostPluginFactory {
            override fun createPlugin(): JetWhaleHostPlugin = UiPlugin()
        },
    )

    private class UiPlugin :
        JetWhaleHostPlugin(),
        JetWhaleHostPluginUi {
        @Composable
        override fun Content() = Unit
    }

    /** Serves one plugin from [jar][reloadPlugin], replacing its factory as a new classloader would. */
    private inner class SwappingPluginFactoryRepository(private var loaded: LoadedHostPlugin) : PluginFactoryRepository {
        override val loadedPlugins: Map<String, LoadedHostPlugin> get() = mapOf(loaded.manifest.pluginId to loaded)
        override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>> = MutableStateFlow(loadedPlugins)
        override val failedJarsFlow: Flow<List<FailedPluginJar>> = MutableStateFlow(emptyList())

        override suspend fun loadPlugin(pluginJarPath: String, expectedSha256: String?) = Unit
        override suspend fun unloadPluginJar(pluginJarPath: String) = Unit
        override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = listOf(pluginId)
        override suspend fun reloadPlugin(pluginJarPath: String, expectedSha256: String?): List<String> {
            loaded = loadedPlugin()
            return listOf(pluginId)
        }

        override fun tryRedefinePlugin(pluginJarPath: String): List<String> = emptyList()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
