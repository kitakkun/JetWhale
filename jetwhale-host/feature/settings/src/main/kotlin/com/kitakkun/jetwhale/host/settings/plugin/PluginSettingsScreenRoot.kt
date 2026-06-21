package com.kitakkun.jetwhale.host.settings.plugin

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberScreenChannel
import com.kitakkun.jetwhale.host.settings.SettingsScreenContext
import soil.query.compose.rememberSubscription
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
context(screenContext: SettingsScreenContext)
fun PluginSettingsScreenRoot() {
    SoilDataBoundary(
        state1 = rememberSubscription(screenContext.loadedPluginsMetaDataSubscriptionKey),
        state2 = rememberSubscription(screenContext.failedPluginJarPathsSubscriptionKey),
        state3 = rememberSubscription(screenContext.untrustedPluginJarPathsSubscriptionKey),
    ) { loadedPlugins, failedJarPaths, untrustedJars ->
        val screenChannel = rememberScreenChannel<PluginSettingsScreenAction, Nothing>()
        val uiState = context(screenContext.presenterContext) {
            pluginSettingsScreenPresenter(
                screenChannel = screenChannel,
                loadedPlugins = loadedPlugins,
                failedJarPaths = failedJarPaths,
                untrustedJarPaths = untrustedJars.paths,
            )
        }

        PluginSettingsScreen(
            uiState = uiState,
            onClickAddPlugin = {
                val selectedJar = selectJarFile() ?: return@PluginSettingsScreen
                screenChannel.send(PluginSettingsScreenAction.PluginJarSelected(selectedJar.path))
            },
            onApproveUntrustedJar = { path ->
                screenChannel.send(PluginSettingsScreenAction.UntrustedJarApproved(path))
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
