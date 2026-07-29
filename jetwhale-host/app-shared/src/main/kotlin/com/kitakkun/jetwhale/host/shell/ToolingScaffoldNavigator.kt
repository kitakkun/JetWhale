package com.kitakkun.jetwhale.host.shell

import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import kotlinx.coroutines.flow.Flow

/**
 * Everything the tooling scaffold — the drawer and the window shell around every screen — can ask
 * the host to navigate to, plus the two bridges it owns to callers outside the composition.
 */
interface ToolingScaffoldNavigator {
    /**
     * Plugin screens asked for by the MCP server. The drawer is the only collector: opening a
     * plugin from outside also has to move the drawer's session and plugin selection, and only the
     * drawer holds that state.
     */
    val externalPluginRequests: Flow<ExternalPluginRequest>

    fun openSettings()

    fun openSettings(page: SettingsScreenPage)

    fun openInfo()

    fun openLogViewer()

    fun openMcpTools(pluginId: String?, sessionId: String?)

    fun navigateHome()

    /** Publishes the drawer's selection, so a caller outside the composition can read it back. */
    fun updateSelection(selectedSessionId: String?, selectedPluginId: String?)
}
