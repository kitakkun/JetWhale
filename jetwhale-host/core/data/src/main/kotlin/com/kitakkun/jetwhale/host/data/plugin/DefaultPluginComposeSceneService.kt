package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.CanvasLayersComposeScene
import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DynamicPluginBridgeProvider
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.WindowInfoUpdater
import com.kitakkun.jetwhale.host.sdk.JetWhaleRawDebugOperationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus

@OptIn(InternalComposeUiApi::class)
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class DefaultPluginComposeSceneService(
    private val pluginBridgeProvider: DynamicPluginBridgeProvider,
    private val pluginInstanceService: PluginInstanceService,
    private val debugWebSocketServer: DebugWebSocketServer,
) : PluginComposeSceneService {
    private val pluginScenes = mutableMapOf<String, PluginComposeScene>()

    override suspend fun getOrCreatePluginScene(
        pluginId: String,
        sessionId: String,
    ): PluginComposeScene {
        println("Creating plugin scene for pluginId=$pluginId, sessionId=$sessionId")
        val pluginInstance = pluginInstanceService.getPluginInstanceForSession(
            pluginId = pluginId,
            sessionId = sessionId,
        ) ?: run {
            error("Plugin instance not found for pluginId=$pluginId, sessionId=$sessionId")
        }
        return pluginScenes.getOrPut("$pluginId:$sessionId") {
            val debugOperationContext = object : JetWhaleRawDebugOperationContext {
                override val coroutineScope: CoroutineScope = debugWebSocketServer.getCoroutineScopeForSession(sessionId) + SupervisorJob()
                override suspend fun dispatch(method: String): String? {
                    return debugWebSocketServer.sendMethod(
                        pluginId = pluginId,
                        sessionId = sessionId,
                        payload = method
                    )
                }
            }

            val windowUpdatableContext = DynamicWindowInfoPlatformContext()

            val composeScene = CanvasLayersComposeScene(
                platformContext = windowUpdatableContext,
            ).apply {
                setContent {
                    pluginBridgeProvider.PluginEntryPoint {
                        pluginInstance.ContentRaw(context = debugOperationContext)
                    }
                }
            }.also {
                println("Plugin scene created for pluginId=$pluginId, sessionId=$sessionId $it")
            }

            PluginComposeScene(
                composeScene = composeScene,
                windowInfoUpdater = windowUpdatableContext,
            )
        }
    }

    override fun disposePluginSceneForSession(sessionId: String) {
        val keysToRemove = pluginScenes.keys.filter { it.endsWith(":$sessionId") }
        for (key in keysToRemove) {
            pluginScenes[key]?.composeScene?.close()
            pluginScenes.remove(key)
        }
    }

    override fun disposePluginScenesForPlugin(pluginId: String) {
        val keysToRemove = pluginScenes.keys.filter { it.startsWith("$pluginId:") }
        for (key in keysToRemove) {
            pluginScenes[key]?.composeScene?.close()
            pluginScenes.remove(key)
        }
    }

    override fun disposeAllPluginScenes() {
        for (scene in pluginScenes.values) {
            scene.composeScene.close()
        }
        pluginScenes.clear()
    }
}

@OptIn(InternalComposeUiApi::class)
private class DynamicWindowInfoPlatformContext(
    private val baseContext: PlatformContext = PlatformContext.Empty(),
) : PlatformContext by baseContext, WindowInfoUpdater {
    private var windowInfoOverride: WindowInfo? = null
    override val windowInfo: WindowInfo get() = windowInfoOverride ?: baseContext.windowInfo

    override fun setWindowInfo(windowInfo: WindowInfo) {
        windowInfoOverride = windowInfo
    }
}
