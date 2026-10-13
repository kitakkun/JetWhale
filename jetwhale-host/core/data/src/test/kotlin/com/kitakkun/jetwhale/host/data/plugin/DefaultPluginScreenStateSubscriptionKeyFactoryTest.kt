package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginDataStoreRepository
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
    private val loadedPlugin = LoadedHostPlugin(
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
    private val service = DefaultPluginInstanceService(
        pluginFactoryRepository = SinglePluginFactoryRepository(loadedPlugin),
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
        val replacedScene = client.awaitSceneFromNewSubscription { true }

        service.unloadPluginInstancesForPlugin(pluginId)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))
        // Within the five seconds soil keeps a subscription alive by default after its last screen
        // closes.
        delay(1.seconds)

        client.awaitSceneFromNewSubscription { it !== replacedScene }
    }

    /** Opens the screen, as the plugin screen does, until it shows a scene [accept] takes, then closes it. */
    private suspend fun SwrCachePlus.awaitSceneFromNewSubscription(accept: (PluginComposeScene) -> Boolean): PluginComposeScene = coroutineScope {
        val subscription = getSubscription(key)
        val receiving = launch { subscription.resume() }
        try {
            withTimeout(1.seconds) {
                subscription.state
                    .mapNotNull { (it.reply.getOrNull() as? PluginScreenState.Ready)?.scene }
                    .first(accept)
            }
        } finally {
            receiving.cancel()
            subscription.close()
        }
    }

    private class UiPlugin :
        JetWhaleHostPlugin(),
        JetWhaleHostPluginUi {
        @Composable
        override fun Content() = Unit
    }
}
