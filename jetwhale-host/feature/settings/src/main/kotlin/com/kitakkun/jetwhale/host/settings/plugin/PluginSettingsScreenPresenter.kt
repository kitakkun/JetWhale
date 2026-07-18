package com.kitakkun.jetwhale.host.settings.plugin

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.ActionEffect
import com.kitakkun.jetwhale.host.architecture.ScreenChannel
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.OfficialPluginCatalog
import com.kitakkun.jetwhale.host.model.PluginInstallProgress
import com.kitakkun.jetwhale.host.model.PluginMetaData
import com.kitakkun.jetwhale.host.model.TrustPluginRequest
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
    failedJars: ImmutableList<FailedPluginJar>,
    untrustedJarPaths: ImmutableList<String>,
    installProgress: PluginInstallProgress?,
): PluginSettingsScreenUiState {
    val pluginInstallMutation = rememberMutation(presenterContext.pluginInstallMutationKey)
    val pluginInstallFromMavenMutation = rememberMutation(presenterContext.pluginInstallFromMavenMutationKey)
    val trustPluginMutation = rememberMutation(presenterContext.trustPluginMutationKey)

    ActionEffect(screenChannel) { action ->
        when (action) {
            is PluginSettingsScreenAction.PluginJarSelected -> {
                pluginInstallMutation.mutateAsync(action.path)
            }

            is PluginSettingsScreenAction.InstallFromMaven -> {
                pluginInstallFromMavenMutation.mutateAsync(action.coordinates)
            }

            is PluginSettingsScreenAction.InstallOfficialPlugin -> {
                // Try each candidate in order (release first, snapshot fallback); only the last
                // failure is left to surface in the mutation's error state.
                val candidates = action.plugin.installCandidatesFor(presenterContext.hostVersionInfo)
                for ((index, coordinates) in candidates.withIndex()) {
                    try {
                        pluginInstallFromMavenMutation.mutateAsync(coordinates)
                        break
                    } catch (e: Exception) {
                        if (index == candidates.lastIndex) throw e
                    }
                }
            }

            is PluginSettingsScreenAction.UntrustedJarApproved -> {
                trustPluginMutation.mutateAsync(TrustPluginRequest(action.path))
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
        officialPlugins = OfficialPluginCatalog.plugins.map { plugin ->
            OfficialPluginUiState(
                plugin = plugin,
                isInstalled = loadedPlugins.any { it.id == plugin.pluginId },
            )
        }.toPersistentList(),
        failedJars = failedJars,
        untrustedJarPaths = untrustedJarPaths,
        isInstalling = isInstalling,
        installProgress = installProgress,
        installError = installError,
    )
}
