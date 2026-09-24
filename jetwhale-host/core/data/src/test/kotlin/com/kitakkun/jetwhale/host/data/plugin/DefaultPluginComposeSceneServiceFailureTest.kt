package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.host.model.DynamicPluginBridgeProvider
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull

/**
 * A plugin UI that throws must not take the host's window down with it: the scene records the
 * exception, stops rendering, and is rebuilt the next time the plugin is opened.
 */
@OptIn(InternalComposeUiApi::class, InternalJetWhaleHostApi::class)
class DefaultPluginComposeSceneServiceFailureTest {
    private val pluginId = "com.example.plugin"
    private val sessionId = "session-1"

    @Test
    fun `a throw while drawing is recorded and rethrown to the renderer`() = runBlocking {
        val armed = mutableStateOf(false)
        val scene = sceneFor(ThrowingUiPlugin(armed, inDraw = true))
        render(scene)

        withContext(Dispatchers.Main) {
            armed.value = true
            Snapshot.sendApplyNotifications()
        }
        assertFailsWith<IllegalStateException> { render(scene) }

        assertEquals("draw boom", scene.failure.value?.message)
    }

    @Test
    fun `a throw while recomposing is recorded instead of escaping to the thread`() = runBlocking {
        val armed = mutableStateOf(false)
        val scene = sceneFor(ThrowingUiPlugin(armed, inDraw = false))
        render(scene)

        withContext(Dispatchers.Main) {
            armed.value = true
            Snapshot.sendApplyNotifications()
        }
        runCatching { render(scene) }

        assertEquals("composition boom", scene.failure.value?.message)
    }

    @Test
    fun `a failed scene is not rendered again and is replaced on the next request`() = runBlocking {
        val armed = mutableStateOf(false)
        val plugin = ThrowingUiPlugin(armed, inDraw = true)
        val service = serviceFor(plugin)
        val failed = service.getOrCreatePluginScene(pluginId, sessionId)
        render(failed)
        withContext(Dispatchers.Main) {
            armed.value = true
            Snapshot.sendApplyNotifications()
        }
        runCatching { render(failed) }
        assertNotNull(failed.failure.value)

        assertFailsWith<IllegalStateException> { render(failed) }
        armed.value = false
        val fresh = service.getOrCreatePluginScene(pluginId, sessionId)

        assertNotSame(failed, fresh)
        assertNull(fresh.failure.value)
    }

    private suspend fun sceneFor(plugin: JetWhaleHostPlugin): PluginComposeScene = serviceFor(plugin).getOrCreatePluginScene(pluginId, sessionId)

    private fun serviceFor(plugin: JetWhaleHostPlugin) = DefaultPluginComposeSceneService(
        pluginBridgeProvider = object : DynamicPluginBridgeProvider {
            @Composable
            override fun PluginEntryPoint(content: @Composable () -> Unit) = content()
        },
        pluginInstanceService = mock<PluginInstanceService> {
            every { getPluginInstanceForSession(pluginId, sessionId) } returns plugin.apply { bindStorage(mock<JetWhalePluginStorage>()) }
        },
    )

    private suspend fun render(scene: PluginComposeScene) = withContext(Dispatchers.Main) {
        scene.composeScene.size = IntSize(SIZE, SIZE)
        scene.render(Canvas(ImageBitmap(SIZE, SIZE)))
    }

    private class ThrowingUiPlugin(
        private val armed: MutableState<Boolean>,
        private val inDraw: Boolean,
    ) : JetWhaleHostPlugin(),
        JetWhaleHostPluginUi {
        @Composable
        override fun Content() {
            val isArmed by armed
            if (!inDraw && isArmed) error("composition boom")
            Box(Modifier.fillMaxSize().drawBehind { if (inDraw && armed.value) error("draw boom") })
        }
    }

    private companion object {
        const val SIZE = 64
    }
}
