package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.DynamicPluginBridgeProvider
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNull

class DefaultPluginComposeSceneServiceTest {
    @Test
    fun `asking for the scene of a plugin with no instance answers null instead of throwing`() = runBlocking {
        val instanceService = mock<PluginInstanceService> {
            every { getPluginInstanceForSession(any(), any()) } returns null
        }
        val service = DefaultPluginComposeSceneService(
            pluginBridgeProvider = mock<DynamicPluginBridgeProvider>(),
            pluginInstanceService = instanceService,
        )

        assertNull(service.getOrCreatePluginScene(pluginId = "com.example.off", sessionId = "host"))
    }
}
