package com.kitakkun.jetwhale.host.shell.navigator

import com.kitakkun.jetwhale.host.settings.SettingsNavKey
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import com.kitakkun.jetwhale.host.settings.logviewer.LogViewerNavKey
import com.kitakkun.jetwhale.host.shell.ExternalPluginRequest
import com.kitakkun.jetwhale.host.shell.InfoNavKey
import com.kitakkun.jetwhale.host.shell.NavCommand
import com.kitakkun.jetwhale.host.shell.NavigationBus
import com.kitakkun.jetwhale.host.shell.ToolingScaffoldNavigator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

@Inject
@ContributesBinding(AppScope::class)
class DefaultToolingScaffoldNavigator(
    private val navigationBus: NavigationBus,
) : ToolingScaffoldNavigator {
    override val externalPluginRequests: Flow<ExternalPluginRequest> get() = navigationBus.externalPluginRequests

    override fun openSettings() {
        navigationBus.send(NavCommand.ShowSingleTop(SettingsNavKey()))
    }

    override fun openSettings(page: SettingsScreenPage) {
        navigationBus.send(NavCommand.ShowSingleTop(SettingsNavKey(initialPage = page)))
    }

    override fun openInfo() {
        navigationBus.send(NavCommand.ShowSingleTop(InfoNavKey))
    }

    override fun openLogViewer() {
        navigationBus.send(NavCommand.ShowSingleTop(LogViewerNavKey))
    }

    override fun openMcpTools(pluginId: String?, sessionId: String?) {
        navigationBus.send(NavCommand.OpenMcpTools(pluginId = pluginId, sessionId = sessionId))
    }

    override fun navigateHome() {
        navigationBus.send(NavCommand.GoHome)
    }

    override fun updateSelection(selectedSessionId: String?, selectedPluginId: String?) {
        navigationBus.updateSelection(
            selectedSessionId = selectedSessionId,
            selectedPluginId = selectedPluginId,
        )
    }
}
