package com.kitakkun.jetwhale.host.settings.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberEventFlow
import soil.query.compose.rememberSubscription
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

context(screenContext: com.kitakkun.jetwhale.host.settings.SettingsScreenContext)
@Composable
fun PluginSettingsScreenRoot() {
    var showMavenDialog by remember { mutableStateOf(false) }

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
            onClickInstallFromMaven = {
                showMavenDialog = true
            },
        )

        if (showMavenDialog) {
            MavenPluginInstallDialog(
                onDismissRequest = { showMavenDialog = false },
                onInstall = { coordinates ->
                    eventFlow.tryEmit(PluginSettingsScreenEvent.InstallFromMaven(coordinates))
                },
            )
        }
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
