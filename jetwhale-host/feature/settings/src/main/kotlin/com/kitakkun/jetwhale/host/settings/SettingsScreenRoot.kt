package com.kitakkun.jetwhale.host.settings

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.settings.general.GeneralSettingsScreenRoot
import com.kitakkun.jetwhale.host.settings.plugin.PluginSettingsScreenRoot
import com.kitakkun.jetwhale.host.settings.server.ServerSettingsScreenRoot

@Composable
context(screenContext: SettingsScreenContext)
fun SettingsScreenRoot(
    onClickClose: () -> Unit,
    initialPage: SettingsScreenPage = SettingsScreenPage.Appearance,
    onOpenLogViewer: () -> Unit = {},
) {
    SettingsScreenScaffoldRoot(
        initialPage = initialPage,
        onClickClose = onClickClose,
    ) { page ->
        // Pages are routed to whichever Root already subscribes to their data, which is not always
        // the section they are filed under: the menu groups settings by what they are about, while a
        // Root groups them by what they need to read.
        when (page.owner) {
            SettingsScreenPageOwner.General -> GeneralSettingsScreenRoot(
                page = page,
                onOpenLogViewer = onOpenLogViewer,
            )

            SettingsScreenPageOwner.Server -> ServerSettingsScreenRoot(page = page)

            SettingsScreenPageOwner.Plugin -> PluginSettingsScreenRoot(page = page)
        }
    }
}
