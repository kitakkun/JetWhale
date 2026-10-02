package com.kitakkun.jetwhale.host.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.settings.general.GeneralSettingsScreenRoot
import com.kitakkun.jetwhale.host.settings.plugin.PluginSettingsScreenRoot
import com.kitakkun.jetwhale.host.settings.server.ServerSettingsScreenRoot

@Composable
context(screenContext: SettingsScreenContext)
fun SettingsScreenRoot(
    onClickClose: () -> Unit,
    onOpenLogViewer: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: SettingsScreenPage = SettingsScreenPage.Appearance,
) {
    SettingsScreenScaffoldRoot(
        onClickClose = onClickClose,
        modifier = modifier,
        initialPage = initialPage,
    ) { page ->
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
