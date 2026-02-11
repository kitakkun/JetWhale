package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.PlatformLayersComposeScene
import androidx.compose.ui.unit.Density
import com.kitakkun.jetwhale.host.model.DynamicPluginBridgeProvider
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@OptIn(InternalComposeUiApi::class)
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class DefaultPluginComposeSceneService(
    private val pluginBridgeProvider: DynamicPluginBridgeProvider,
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val pluginInstanceService: PluginInstanceService,
) : PluginComposeSceneService {
    private val pluginScenes = mutableMapOf<String, ComposeScene>()

    override suspend fun getOrCreatePluginScene(
        pluginId: String,
        sessionId: String,
        density: Density,
    ): ComposeScene {
        println("Creating plugin scene for pluginId=$pluginId, sessionId=$sessionId")
        val pluginInstance = pluginInstanceService.getOrPutPluginInstanceForSession(
            pluginId = pluginId,
            sessionId = sessionId,
            pluginFactory = pluginFactoryRepository.loadedPluginFactories.getValue(pluginId)
        )
        return pluginScenes.getOrPut("$pluginId:$sessionId") {
            PlatformLayersComposeScene(density = density).apply {
                setContent {
                    pluginBridgeProvider.PluginEntryPoint { context ->
                        pluginInstance.ContentRaw(context)
                    }
                }
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
