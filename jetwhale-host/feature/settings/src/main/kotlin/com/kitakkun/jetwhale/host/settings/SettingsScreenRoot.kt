package com.kitakkun.jetwhale.host.settings

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.settings.general.GeneralSettingsScreenRoot
import com.kitakkun.jetwhale.host.settings.plugin.PluginSettingsScreenRoot
import com.kitakkun.jetwhale.host.settings.server.ServerSettingsScreenRoot

@Composable
context(screenContext: SettingsScreenContext)
fun SettingsScreenRoot(
    onClickClose: () -> Unit,
    initialMenu: SettingsScreenMenu = SettingsScreenMenu.General,
    onOpenLogViewer: () -> Unit = {},
) {
    SettingsScreenScaffoldRoot(
        initialMenu = initialMenu,
        onClickClose = onClickClose,
    ) {
        when (it) {
            SettingsScreenMenu.General -> GeneralSettingsScreenRoot(
                onOpenLogViewer = onOpenLogViewer,
            )

            SettingsScreenMenu.Server -> ServerSettingsScreenRoot()

            SettingsScreenMenu.Plugins -> PluginSettingsScreenRoot()
        }
    }
}
