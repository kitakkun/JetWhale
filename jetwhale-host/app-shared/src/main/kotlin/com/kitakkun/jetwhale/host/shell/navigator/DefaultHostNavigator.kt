package com.kitakkun.jetwhale.host.shell.navigator

import com.kitakkun.jetwhale.host.model.HostNavigator
import com.kitakkun.jetwhale.host.model.HostSettingsSection
import com.kitakkun.jetwhale.host.model.HostViewState
import com.kitakkun.jetwhale.host.settings.SettingsNavKey
import com.kitakkun.jetwhale.host.settings.logviewer.LogViewerNavKey
import com.kitakkun.jetwhale.host.shell.ExternalPluginRequest
import com.kitakkun.jetwhale.host.shell.InfoNavKey
import com.kitakkun.jetwhale.host.shell.NavCommand
import com.kitakkun.jetwhale.host.shell.NavigationBus
import com.kitakkun.jetwhale.host.shell.toPage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.StateFlow

@Inject
@ContributesBinding(AppScope::class)
class DefaultHostNavigator(
    private val navigationBus: NavigationBus,
) : HostNavigator {
    override val currentView: StateFlow<HostViewState?> get() = navigationBus.currentView

    override fun navigateHome() {
        navigationBus.send(NavCommand.GoHome)
    }

    override fun navigateToPlugin(pluginId: String, sessionId: String?) {
        navigationBus.requestPlugin(ExternalPluginRequest(pluginId = pluginId, sessionId = sessionId))
    }

    override fun navigateToSettings(section: HostSettingsSection) {
        navigationBus.send(NavCommand.ShowSingleTop(SettingsNavKey(initialPage = section.toPage())))
    }

    override fun navigateToInfo() {
        navigationBus.send(NavCommand.ShowSingleTop(InfoNavKey))
    }

    override fun navigateToLogViewer() {
        navigationBus.send(NavCommand.ShowSingleTop(LogViewerNavKey))
    }
}
