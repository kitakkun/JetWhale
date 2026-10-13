package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.DynamicPluginBridgeProvider
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalComposeUiApi::class, InternalJetWhaleHostApi::class)
class DefaultPluginComposeSceneServiceTest {
    private val plugin = WindowSizeReadingPlugin().apply { bindStorage(mock()) }

    private val service = DefaultPluginComposeSceneService(
        pluginBridgeProvider = PassThroughBridgeProvider,
        pluginInstanceService = mock<PluginInstanceService> {
            every { getPluginInstanceForSession(any(), any()) } returns plugin
        },
    )

    @Test
    fun `an unchanged window size does not recompose the plugin UI`() = runBlocking {
        val scene = service.getOrCreatePluginScene(pluginId = "plugin", sessionId = "session")
        withContext(Dispatchers.Main) {
            scene.showAt(IntSize(400, 300))
            val compositionsBefore = plugin.compositions

            scene.showAt(IntSize(400, 300))

            assertEquals(compositionsBefore, plugin.compositions)
        }
    }

    @Test
    fun `a changed window size reaches the plugin UI`() = runBlocking {
        val scene = service.getOrCreatePluginScene(pluginId = "plugin", sessionId = "session")
        withContext(Dispatchers.Main) {
            scene.showAt(IntSize(400, 300))

            scene.showAt(IntSize(800, 600))

            assertEquals(IntSize(800, 600), plugin.observedContainerSize)
        }
    }

    private fun PluginComposeScene.showAt(size: IntSize) {
        composeScene.size = size
        windowInfoUpdater.updateWindowSize(intSize = size, dpSize = DpSize(size.width.dp, size.height.dp))
        Snapshot.sendApplyNotifications()
        render(Canvas(ImageBitmap(1, 1)))
    }
}

private class WindowSizeReadingPlugin :
    JetWhaleHostPlugin(),
    JetWhaleHostPluginUi {
    var compositions = 0
    var observedContainerSize: IntSize? = null

    @Composable
    override fun Content() {
        val containerSize = LocalWindowInfo.current.containerSize
        SideEffect {
            compositions++
            observedContainerSize = containerSize
        }
    }
}

private object PassThroughBridgeProvider : DynamicPluginBridgeProvider {
    @Composable
    override fun PluginEntryPoint(content: @Composable () -> Unit) {
        content()
    }
}
