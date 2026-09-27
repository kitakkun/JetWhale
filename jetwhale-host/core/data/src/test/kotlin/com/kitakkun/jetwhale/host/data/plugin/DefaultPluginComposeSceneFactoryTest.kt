package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import com.kitakkun.jetwhale.host.model.DynamicPluginBridgeProvider
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DefaultPluginComposeSceneFactoryTest {
    private val passThroughBridge = object : DynamicPluginBridgeProvider {
        @Composable
        override fun PluginEntryPoint(content: @Composable () -> Unit) = content()
    }

    @Test
    fun `a scene whose content throws while it is first composed is closed`() = runBlocking<Unit> {
        val factory = DefaultPluginComposeSceneFactory(passThroughBridge)
        val runningBefore = Recomposer.runningRecomposers.value

        withContext(Dispatchers.Main) { assertFailsWith<IllegalStateException> { factory.createScene(boundPlugin()) { error("content broke") } } }

        // A scene that stays open keeps its recomposer running.
        withTimeout(5_000) { Recomposer.runningRecomposers.first { (it - runningBefore).isEmpty() } }
    }

    @OptIn(InternalJetWhaleHostApi::class)
    private fun boundPlugin(): JetWhaleHostPlugin = object : JetWhaleHostPlugin() {}.apply {
        bindStorage(mock<JetWhalePluginStorage>())
    }
}
