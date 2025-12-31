package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.PlatformLayersComposeScene
import androidx.compose.ui.unit.Density
import com.kitakkun.jetwhale.debugger.host.sdk.JetWhaleContentUIBuilderContext
import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DynamicPluginBridgeProvider
import com.kitakkun.jetwhale.host.model.PluginComposeSceneRepository
import com.kitakkun.jetwhale.host.model.PluginRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

@OptIn(InternalComposeUiApi::class)
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class DefaultPluginComposeSceneRepository(
    private val pluginBridgeProvider: DynamicPluginBridgeProvider,
    private val pluginRepository: PluginRepository,
    private val debugWebSocketServer: DebugWebSocketServer,
    private val json: Json,
) : PluginComposeSceneRepository {
    private val pluginScenes = mutableMapOf<String, ComposeScene>()

    override suspend fun getOrCreatePluginScene(
        pluginId: String,
        sessionId: String,
        density: Density,
    ): ComposeScene {
        println("Creating plugin scene for pluginId=$pluginId, sessionId=$sessionId")
        val pluginInstance = pluginRepository.getOrPutPluginInstanceForSession(
            pluginId = pluginId,
            sessionId = sessionId,
        )
        return pluginScenes.getOrPut("$pluginId:$sessionId") {
            PlatformLayersComposeScene(density = density).apply {
                setContent {
                    pluginBridgeProvider.PluginEntryPoint {
                        pluginInstance.Content(
                            context = object : JetWhaleContentUIBuilderContext {
                                override suspend fun <Method, MethodResult> dispatch(
                                    methodSerializer: KSerializer<Method>,
                                    methodResultSerializer: KSerializer<MethodResult>,
                                    value: Method
                                ): MethodResult? {
                                    val serializedMethod = json.encodeToString(methodSerializer, value)
                                    return debugWebSocketServer.sendMessage(
                                        pluginId = pluginId,
                                        sessionId = sessionId,
                                        message = serializedMethod,
                                    )?.let { serializedMethodResult ->
                                        json.decodeFromString(methodResultSerializer, serializedMethodResult)
                                    }
                                }
                            }
                        )
                    }
                }
            }.also {
                println("Plugin scene created for pluginId=$pluginId, sessionId=$sessionId $it")
            }
        }
    }

    override fun disposePluginSceneForSession(sessionId: String) {
        val keysToRemove = pluginScenes.keys.filter { it.endsWith(":$sessionId") }
        for (key in keysToRemove) {
            pluginScenes[key]?.close()
            pluginScenes.remove(key)
        }
    }
}
