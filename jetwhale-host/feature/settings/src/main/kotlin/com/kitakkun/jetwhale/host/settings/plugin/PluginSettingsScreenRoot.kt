package com.kitakkun.jetwhale.host.settings.plugin

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberEventFlow
import soil.query.compose.rememberSubscription
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

context(screenContext: com.kitakkun.jetwhale.host.settings.SettingsScreenContext)
@Composable
fun PluginSettingsScreenRoot() {
    SoilDataBoundary(
        state = rememberSubscription(screenContext.loadedPluginsMetaDataSubscriptionKey),
    ) { loadedPlugins ->
        val eventFlow = rememberEventFlow<PluginSettingsScreenEvent>()
        val uiState = pluginSettingsScreenPresenter(
            eventFlow = eventFlow,
            loadedPlugins = loadedPlugins,
        )

        PluginSettingsScreen(
            uiState = uiState,
            onClickAddPlugin = {
                val selectedJar = selectJarFile() ?: return@PluginSettingsScreen
                eventFlow.tryEmit(PluginSettingsScreenEvent.PluginJarSelected(selectedJar.path))
            },
        )
    }
}

private fun selectJarFile(parent: Frame? = null): File? {
    val dialog = FileDialog(parent, "Select Plugin Jar", FileDialog.LOAD).apply {
        isVisible = true
    }

    val dir = dialog.directory ?: return null
    val file = dialog.file ?: return null

    return File(dir, file)
}
