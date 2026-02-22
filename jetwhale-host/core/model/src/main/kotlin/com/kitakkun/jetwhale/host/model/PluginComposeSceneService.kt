package com.kitakkun.jetwhale.host.model

import androidx.compose.ui.InternalComposeUiApi

interface PluginComposeSceneService {
    @OptIn(InternalComposeUiApi::class)
    suspend fun getOrCreatePluginScene(
        pluginId: String,
        sessionId: String,
    ): PluginComposeScene

    fun disposePluginSceneForSession(sessionId: String)

    fun disposePluginScenesForPlugin(pluginId: String)

    fun disposeAllPluginScenes()
}
