package com.kitakkun.jetwhale.host.model

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.unit.Density

interface PluginComposeSceneService {
    /**
     * Records the density newly created scenes are seeded with.
     *
     * A scene the host window renders is given the window's density on layout, but one created for
     * a caller that never displays it — a screenshot, say — would otherwise sit at the ComposeScene
     * default of 1.0 and lay out as if the display were non-HiDPI. Seeding it here keeps both paths
     * showing the same thing.
     */
    fun updateHostDensity(density: Density)

    @OptIn(InternalComposeUiApi::class)
    suspend fun getOrCreatePluginScene(
        pluginId: String,
        sessionId: String,
    ): PluginComposeScene

    fun disposePluginSceneForSession(sessionId: String)

    fun disposePluginScenesForPlugin(pluginId: String)

    fun disposeAllPluginScenes()
}
