package com.kitakkun.jetwhale.host.shell.navigator

import com.kitakkun.jetwhale.host.model.PoppedOutPlugin
import com.kitakkun.jetwhale.host.plugin.PluginNavKey
import com.kitakkun.jetwhale.host.plugin.PluginNavigator
import com.kitakkun.jetwhale.host.plugin.PluginPopoutNavKey
import com.kitakkun.jetwhale.host.shell.NavCommand
import com.kitakkun.jetwhale.host.shell.NavigationBus
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.StateFlow

@Inject
@ContributesBinding(AppScope::class)
class DefaultPluginNavigator(
    private val navigationBus: NavigationBus,
) : PluginNavigator {
    override val poppedOutPlugins: StateFlow<List<PoppedOutPlugin>> get() = navigationBus.poppedOutPlugins

    override fun openPlugin(pluginId: String, sessionId: String) {
        navigationBus.send(
            NavCommand.ShowSingleTop(
                PluginNavKey(
                    pluginId = pluginId,
                    sessionId = sessionId,
                ),
            ),
        )
    }

    override fun popOut(pluginId: String, pluginName: String, sessionId: String) {
        navigationBus.send(
            NavCommand.ShowSingleTop(
                PluginPopoutNavKey(
                    pluginId = pluginId,
                    sessionId = sessionId,
                    pluginName = pluginName,
                ),
            ),
        )
    }

    override fun bringBackToMainWindow(pluginId: String, sessionId: String) {
        navigationBus.send(
            NavCommand.BringPluginBackToMainWindow(
                pluginId = pluginId,
                sessionId = sessionId,
            ),
        )
    }

    override fun closeAllPluginScreens() {
        navigationBus.send(NavCommand.CloseAllPluginScreens)
    }

    override fun closePluginScreensForSession(sessionId: String) {
        navigationBus.send(NavCommand.ClosePluginScreensForSession(sessionId))
    }

    override fun closePluginScreensForPlugin(pluginId: String) {
        navigationBus.send(NavCommand.ClosePluginScreensForPlugin(pluginId))
    }

    override fun followPluginToSession(newSessionId: String, availablePluginIds: Set<String>) {
        navigationBus.send(
            NavCommand.FollowPluginToSession(
                newSessionId = newSessionId,
                availablePluginIds = availablePluginIds,
            ),
        )
    }
}
