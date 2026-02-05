package com.kitakkun.jetwhale.host.model

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density

interface PluginComposeSceneService {
    @OptIn(InternalComposeUiApi::class)
    suspend fun getOrCreatePluginScene(
        pluginId: String,
        sessionId: String,
        density: Density,
    ): ComposeScene

    fun disposePluginSceneForSession(sessionId: String)
}
