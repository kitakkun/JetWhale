package com.kitakkun.jetwhale.host.settings.plugin

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.ActionEffect
import com.kitakkun.jetwhale.host.architecture.ScreenChannel
import com.kitakkun.jetwhale.host.model.PluginMetaData
import com.kitakkun.jetwhale.host.settings.SettingsPresenterContext
import com.kitakkun.jetwhale.host.settings.component.PluginInfoUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import soil.query.compose.rememberMutation

@Composable
context(presenterContext: SettingsPresenterContext)
fun pluginSettingsScreenPresenter(
    screenChannel: ScreenChannel<PluginSettingsScreenAction, Nothing>,
    loadedPlugins: ImmutableList<PluginMetaData>,
    failedJarPaths: ImmutableList<String>,
): PluginSettingsScreenUiState {
    val pluginInstallMutation = rememberMutation(presenterContext.pluginInstallMutationKey)
    val pluginInstallFromMavenMutation = rememberMutation(presenterContext.pluginInstallFromMavenMutationKey)

    ActionEffect(screenChannel) { action ->
        when (action) {
            is PluginSettingsScreenAction.PluginJarSelected -> {
                pluginInstallMutation.mutateAsync(action.path)
            }

            is PluginSettingsScreenAction.InstallFromMaven -> {
                pluginInstallFromMavenMutation.mutate(action.coordinates)
            }
        }
    }

    val isInstalling = pluginInstallMutation.isPending || pluginInstallFromMavenMutation.isPending
    val installError = pluginInstallFromMavenMutation.error?.message
        ?: pluginInstallMutation.error?.message

    return PluginSettingsScreenUiState(
        plugins = loadedPlugins.map {
            PluginInfoUiState(
                id = it.id,
                name = it.name,
                version = it.version,
            )
        }.toPersistentList(),
        failedJarPaths = failedJarPaths,
        isInstalling = isInstalling,
        installError = installError,
    )
}
