package com.kitakkun.jetwhale.host.settings.plugin

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.ActionEffect
import com.kitakkun.jetwhale.host.architecture.ScreenChannel
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.OfficialPluginCatalog
import com.kitakkun.jetwhale.host.model.PluginInstallJob
import com.kitakkun.jetwhale.host.model.PluginInstallRequest
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
    installJobs: ImmutableList<PluginInstallJob>,
    signPluginTrustRegistry: Boolean,
): PluginSettingsScreenUiState {
    val pluginInstallMutation = rememberMutation(presenterContext.pluginInstallMutationKey)
    // These three only queue work in the install service and return: the install itself outlives
    // this screen, which a mutation torn down with it could not.
    val startPluginInstallMutation = rememberMutation(presenterContext.startPluginInstallMutationKey)
    val cancelPluginInstallMutation = rememberMutation(presenterContext.cancelPluginInstallMutationKey)
    val dismissPluginInstallMutation = rememberMutation(presenterContext.dismissPluginInstallMutationKey)
    val trustPluginMutation = rememberMutation(presenterContext.trustPluginMutationKey)
    val signPluginTrustRegistryMutation = rememberMutation(presenterContext.signPluginTrustRegistryMutationKey)

    ActionEffect(screenChannel) { action ->
        when (action) {
            is PluginSettingsScreenAction.PluginJarSelected -> {
                pluginInstallMutation.mutateAsync(action.path)
            }

            is PluginSettingsScreenAction.InstallFromMaven -> {
                startPluginInstallMutation.mutateAsync(PluginInstallRequest.Maven(action.coordinates))
            }

            is PluginSettingsScreenAction.InstallOfficialPlugin -> {
                startPluginInstallMutation.mutateAsync(PluginInstallRequest.Official(action.plugin))
            }

            is PluginSettingsScreenAction.CancelInstall -> {
                cancelPluginInstallMutation.mutateAsync(action.jobId)
            }

            is PluginSettingsScreenAction.RetryInstall -> {
                startPluginInstallMutation.mutateAsync(action.request)
            }

            is PluginSettingsScreenAction.DismissInstall -> {
                dismissPluginInstallMutation.mutateAsync(action.jobId)
            }

            is PluginSettingsScreenAction.UntrustedJarApproved -> {
                trustPluginMutation.mutateAsync(TrustPluginRequest(action.path, approvedSha256 = null))
            }

            is PluginSettingsScreenAction.ChangeSignPluginTrustRegistry -> {
                signPluginTrustRegistryMutation.mutateAsync(action.enabled)
            }
        }
    }

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
                installJob = installJobs.lastOrNull { it.request.pluginId == plugin.pluginId },
            )
        }.toPersistentList(),
        failedJars = failedJars,
        untrustedJarPaths = untrustedJarPaths,
        signPluginTrustRegistry = signPluginTrustRegistry,
        installJobs = installJobs,
        isAddingFromFile = pluginInstallMutation.isPending,
        addFromFileError = pluginInstallMutation.error?.message,
    )
}
