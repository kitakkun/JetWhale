package com.kitakkun.jetwhale.host.settings.plugin

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.EventEffect
import com.kitakkun.jetwhale.host.architecture.EventFlow
import com.kitakkun.jetwhale.host.model.PluginMetaData
import com.kitakkun.jetwhale.host.settings.SettingsScreenContext
import com.kitakkun.jetwhale.host.settings.component.PluginInfoUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import soil.query.compose.rememberMutation

context(screenContext: SettingsScreenContext)
@Composable
fun pluginSettingsScreenPresenter(
    eventFlow: EventFlow<PluginSettingsScreenEvent>,
    loadedPlugins: ImmutableList<PluginMetaData>,
): PluginSettingsScreenUiState {
    val pluginInstallMutation = rememberMutation(screenContext.pluginInstallMutationKey)
    val pluginInstallFromMavenMutation = rememberMutation(screenContext.pluginInstallFromMavenMutationKey)

    EventEffect(eventFlow) { event ->
        when (event) {
            is PluginSettingsScreenEvent.PluginJarSelected -> {
                pluginInstallMutation.mutate(event.path)
            }
            is PluginSettingsScreenEvent.InstallFromMaven -> {
                pluginInstallFromMavenMutation.mutate(event.coordinates)
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
    )
}
