package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginDataStoreRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginScreenState
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import soil.query.SwrCachePlus
import soil.query.SwrCachePlusPolicy
import soil.query.annotation.ExperimentalSoilQueryApi
import soil.query.core.getOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalSoilQueryApi::class, ExperimentalCoroutinesApi::class)
class DefaultPluginScreenStateSubscriptionKeyFactoryTest {
    private val pluginId = "com.example.plugin"
    private val sessionId = "session-1"

    private val storage = mock<JetWhalePluginStorage>()
    private val service = DefaultPluginInstanceService(
        pluginFactoryRepository = SinglePluginFactoryRepository(),
        frameSender = mock(),
        pluginDataStoreRepository = mock<PluginDataStoreRepository> {
            every { storageFor(any()) } returns storage
        },
        pluginComposeSceneFactory = RecordingPluginComposeSceneFactory(),
    )
    private val key = DefaultPluginScreenStateSubscriptionKeyFactory(service).create(pluginId, sessionId)

    @BeforeTest
    fun runMainOnTestScheduler() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun restoreMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a screen reopened soon after its instance was replaced shows the replacement's scene`() = runTest {
        val client = SwrCachePlus(SwrCachePlusPolicy(backgroundScope))
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))
        val replacedScene = client.showScene { true }

        service.unloadPluginInstancesForPlugin(pluginId)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))
        // Within the five seconds soil keeps a subscription alive by default after its last screen
        // closes.
        delay(1.seconds)

        client.showScene { it !== replacedScene }
    }

    /** Opens the screen, as the plugin screen does, until it shows a scene [accept] takes, then closes it. */
    private suspend fun SwrCachePlus.showScene(accept: (PluginComposeScene) -> Boolean): PluginComposeScene = coroutineScope {
        val screen = getSubscription(key)
        val receiving = launch { screen.resume() }
        try {
            withTimeout(1.seconds) {
                screen.state
                    .mapNotNull { (it.reply.getOrNull() as? PluginScreenState.Ready)?.scene }
                    .first(accept)
            }
        } finally {
            receiving.cancel()
            screen.close()
        }
    }

    private inner class SinglePluginFactoryRepository : PluginFactoryRepository {
        private val loaded = LoadedHostPlugin(
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

        override val loadedPlugins: Map<String, LoadedHostPlugin> = mapOf(pluginId to loaded)
        override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>> = MutableStateFlow(loadedPlugins)
        override val failedJarsFlow: Flow<List<FailedPluginJar>> = MutableStateFlow(emptyList())

        override suspend fun loadPlugin(pluginJarPath: String, expectedSha256: String?) = Unit
        override suspend fun unloadPluginJar(pluginJarPath: String) = Unit
        override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = emptyList()
        override suspend fun reloadPlugin(pluginJarPath: String, expectedSha256: String?): List<String> = emptyList()
        override fun tryRedefinePlugin(pluginJarPath: String): List<String> = emptyList()
    }

    private class UiPlugin :
        JetWhaleHostPlugin(),
        JetWhaleHostPluginUi {
        @Composable
        override fun Content() = Unit
    }
}
