package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
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
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(InternalComposeUiApi::class, InternalJetWhaleHostApi::class)
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

    @Test
    fun `an unchanged window size does not recompose the plugin UI`() = runBlocking {
        val plugin = WindowSizeReadingPlugin().apply { bindStorage(mock()) }
        withContext(Dispatchers.Main) {
            val scene = DefaultPluginComposeSceneFactory(passThroughBridge).createScene(plugin) { plugin.Content() }
            scene.showAt(IntSize(400, 300))
            val compositionsBefore = plugin.compositions

            scene.showAt(IntSize(400, 300))

            assertEquals(compositionsBefore, plugin.compositions)
        }
    }

    @Test
    fun `a changed window size reaches the plugin UI`() = runBlocking {
        val plugin = WindowSizeReadingPlugin().apply { bindStorage(mock()) }
        withContext(Dispatchers.Main) {
            val scene = DefaultPluginComposeSceneFactory(passThroughBridge).createScene(plugin) { plugin.Content() }
            scene.showAt(IntSize(400, 300))

            scene.showAt(IntSize(800, 600))

            assertEquals(IntSize(800, 600), plugin.observedContainerSize)
        }
    }

    @Test
    fun `a scene no window has shown yet reports the size it gets once the window shows it`() = runBlocking {
        val pluginArea = IntSize(1998, 1544)
        val factory = DefaultPluginComposeSceneFactory(passThroughBridge)
        factory.updateHostPluginArea(intSize = pluginArea, dpSize = DpSize(999.dp, 772.dp))
        withContext(Dispatchers.Main) {
            val scene = factory.createScene(boundPlugin()) {}
            val sizeBeforeShown = scene.windowInfoUpdater.currentIntSize

            scene.showAt(pluginArea)

            assertEquals(scene.windowInfoUpdater.currentIntSize, sizeBeforeShown)
        }
    }

    @Test
    fun `a scene no window has shown yet follows the plugin area as the window resizes`() = runBlocking {
        val plugin = WindowSizeReadingPlugin().apply { bindStorage(mock()) }
        val factory = DefaultPluginComposeSceneFactory(passThroughBridge)
        withContext(Dispatchers.Main) {
            val scene = factory.createScene(plugin) { plugin.Content() }

            factory.updateHostPluginArea(intSize = IntSize(800, 600), dpSize = DpSize(400.dp, 300.dp))
            Snapshot.sendApplyNotifications()
            scene.render(Canvas(ImageBitmap(1, 1)))

            assertEquals(IntSize(800, 600), plugin.observedContainerSize)
        }
    }

    @Test
    fun `a window that shows a scene keeps its own size when the plugin area changes`() = runBlocking {
        val factory = DefaultPluginComposeSceneFactory(passThroughBridge)
        withContext(Dispatchers.Main) {
            val scene = factory.createScene(boundPlugin()) {}
            scene.showAt(IntSize(400, 300))

            factory.updateHostPluginArea(intSize = IntSize(800, 600), dpSize = DpSize(800.dp, 600.dp))

            assertEquals(IntSize(400, 300), scene.windowInfoUpdater.currentIntSize)
        }
    }

    private fun PluginComposeScene.showAt(size: IntSize) {
        composeScene.size = size
        windowInfoUpdater.updateWindowSize(intSize = size, dpSize = DpSize(size.width.dp, size.height.dp))
        Snapshot.sendApplyNotifications()
        render(Canvas(ImageBitmap(1, 1)))
    }

    private fun boundPlugin(): JetWhaleHostPlugin = object : JetWhaleHostPlugin() {}.apply {
        bindStorage(mock<JetWhalePluginStorage>())
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
