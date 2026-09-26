package com.kitakkun.jetwhale.host.model

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.unit.Density

interface PluginComposeSceneService {
    /**
     * Records the density newly created scenes are seeded with.
     *
     * This is a starting value, not the authority: whichever window ends up rendering a scene
     * assigns its own density, since a popped-out plugin gets its own window and can sit on a
     * display with a different scale factor. The seed only decides how a scene lays out until then
     * — without it, one created for a caller that never displays it (a screenshot, say) would sit
     * at the ComposeScene default of 1.0 and lay out as if the display were non-HiDPI.
     */
    fun updateHostDensity(density: Density)

    /**
     * The scene composing [pluginId]'s instance in [sessionId], or null while there is no such
     * instance (the plugin is disabled, not installed for that session, or not created yet) or it
     * keeps being replaced while its scene is built.
     */
    @OptIn(InternalComposeUiApi::class)
    suspend fun getOrCreatePluginScene(
        pluginId: String,
        sessionId: String,
    ): PluginComposeScene?

    fun disposePluginSceneForSession(sessionId: String)

    fun disposePluginScenesForPlugin(pluginId: String)

    /** Disposes every connected app's scenes, keeping [HostSession]'s: the server stopping ends apps, not those. */
    fun disposeAppSessionPluginScenes()
}
