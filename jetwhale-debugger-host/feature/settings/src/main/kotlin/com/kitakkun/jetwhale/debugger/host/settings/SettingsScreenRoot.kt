package com.kitakkun.jetwhale.debugger.host.settings

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.debugger.host.settings.general.GeneralSettingsScreenRoot
import com.kitakkun.jetwhale.debugger.host.settings.plugin.PluginSettingsScreenRoot
import com.kitakkun.jetwhale.debugger.host.settings.server.ServerSettingsScreenRoot

@Composable
context(screenContext: SettingsScreenContext)
fun SettingsScreenRoot(
    onClickClose: () -> Unit,
) {
    SettingsScreenScaffoldRoot(
        onClickClose = onClickClose,
    ) {
        when (it) {
            SettingsScreenSegmentedMenu.General -> GeneralSettingsScreenRoot()
            SettingsScreenSegmentedMenu.Server -> ServerSettingsScreenRoot()
            SettingsScreenSegmentedMenu.Plugins -> PluginSettingsScreenRoot()
        }
    }
}
