package com.kitakkun.jetwhale.host.settings

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.settings.general.GeneralSettingsScreenRoot
import com.kitakkun.jetwhale.host.settings.plugin.PluginSettingsScreenRoot
import com.kitakkun.jetwhale.host.settings.server.ServerSettingsScreenRoot

@Composable
context(screenContext: SettingsScreenContext)
fun SettingsScreenRoot(
    onClickClose: () -> Unit,
    onOpenLogViewer: () -> Unit = {},
) {
    SettingsScreenScaffoldRoot(
        onClickClose = onClickClose,
    ) {
        when (it) {
            SettingsScreenSegmentedMenu.General -> GeneralSettingsScreenRoot(
                onOpenLogViewer = onOpenLogViewer,
            )
            SettingsScreenSegmentedMenu.Server -> ServerSettingsScreenRoot()
            SettingsScreenSegmentedMenu.Plugins -> PluginSettingsScreenRoot()
        }
    }
}
