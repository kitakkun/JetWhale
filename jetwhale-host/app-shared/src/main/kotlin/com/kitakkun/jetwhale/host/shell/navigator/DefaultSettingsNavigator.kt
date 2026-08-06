package com.kitakkun.jetwhale.host.shell.navigator

import com.kitakkun.jetwhale.host.settings.SettingsNavKey
import com.kitakkun.jetwhale.host.settings.SettingsNavigator
import com.kitakkun.jetwhale.host.settings.logviewer.LogViewerNavKey
import com.kitakkun.jetwhale.host.shell.NavCommand
import com.kitakkun.jetwhale.host.shell.NavigationBus
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@Inject
@ContributesBinding(AppScope::class)
class DefaultSettingsNavigator(
    private val navigationBus: NavigationBus,
) : SettingsNavigator {
    override fun close() {
        navigationBus.send(NavCommand.CloseAllOfType(SettingsNavKey::class))
    }

    override fun openLogViewer() {
        // Underneath the settings dialog: the dialog stays on top, and closing it reveals the
        // viewer rather than closing it too.
        navigationBus.send(NavCommand.ShowSingleTopAt(index = 0, key = LogViewerNavKey))
    }
}
