package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginComposeSceneFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.CopyOnWriteArrayList

/** Real, empty scenes that record which of them were created and which were closed. */
@OptIn(InternalComposeUiApi::class)
internal class RecordingPluginComposeSceneFactory : PluginComposeSceneFactory {
    val created: MutableList<PluginComposeScene> = CopyOnWriteArrayList()

    /** Scenes whose composition was disposed, which is what closing a scene does. */
    val closed = MutableStateFlow<Set<PluginComposeScene>>(emptySet())

    /** Thrown by the next scene creation, as content that throws while it is first composed would be. */
    @Volatile
    var failNext: Throwable? = null

    override fun updateHostDensity(density: Density) = Unit

    override fun updateHostPluginArea(intSize: IntSize, dpSize: DpSize) = Unit

    override fun createScene(plugin: JetWhaleHostPlugin, content: @Composable () -> Unit): PluginComposeScene {
        failNext?.let { failure ->
            failNext = null
            throw failure
        }
        lateinit var scene: PluginComposeScene
        val composeScene = CanvasLayersComposeScene()
        composeScene.setContent {
            DisposableEffect(Unit) {
                onDispose { closed.update { it + scene } }
            }
        }
        scene = PluginComposeScene(
            composeScene = composeScene,
            windowInfoUpdater = mock(),
            semanticsOwners = emptySet(),
            isMcpCapture = mutableStateOf(false),
            pointerIcon = mutableStateOf(PointerIcon.Default),
        )
        created += scene
        return scene
    }
}
