package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.model.DynamicPluginBridgeProvider
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DefaultPluginComposeSceneServiceTest {
    private val passThroughBridge = object : DynamicPluginBridgeProvider {
        @Composable
        override fun PluginEntryPoint(content: @Composable () -> Unit) = content()
    }

    @Test
    fun `asking for the scene of a plugin with no instance answers null instead of throwing`() = runBlocking {
        val instanceService = mock<PluginInstanceService> {
            every { getPluginInstanceForSession(any(), any()) } returns null
        }
        val service = DefaultPluginComposeSceneService(
            pluginBridgeProvider = passThroughBridge,
            pluginInstanceService = instanceService,
        )

        assertNull(service.getOrCreatePluginScene(pluginId = "com.example.off", sessionId = "host"))
    }

    @Test
    fun `an instance replaced while its scene is built is followed by the replacement's scene`() = runBlocking<Unit> {
        val replaced = boundPlugin()
        val replacement = boundPlugin()
        var lookups = 0
        val instanceService = mock<PluginInstanceService> {
            // The first lookup sees the instance a scene is built for; every later one sees the
            // replacement that landed while it was being composed.
            every { getPluginInstanceForSession(any(), any()) } calls { if (lookups++ == 0) replaced else replacement }
        }
        val service = DefaultPluginComposeSceneService(
            pluginBridgeProvider = passThroughBridge,
            pluginInstanceService = instanceService,
        )

        assertNotNull(service.getOrCreatePluginScene(pluginId = "com.example.replaced", sessionId = "host"))
    }

    @Test
    fun `an instance replaced during every attempt to build its scene yields no scene`() = runBlocking {
        val plugins = listOf(boundPlugin(), boundPlugin())
        var lookups = 0
        val instanceService = mock<PluginInstanceService> {
            every { getPluginInstanceForSession(any(), any()) } calls { plugins[lookups++ % 2] }
        }
        val service = DefaultPluginComposeSceneService(
            pluginBridgeProvider = passThroughBridge,
            pluginInstanceService = instanceService,
        )

        assertNull(service.getOrCreatePluginScene(pluginId = "com.example.churning", sessionId = "host"))
    }

    @OptIn(InternalJetWhaleHostApi::class)
    private fun boundPlugin(): JetWhaleHostPlugin = object : JetWhaleHostPlugin() {}.apply {
        bindStorage(mock<JetWhalePluginStorage>())
    }
}
