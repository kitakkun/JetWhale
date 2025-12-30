package com.kitakkun.jetwhale.debugger.host.settings.plugin

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.debugger.host.architecture.EventEffect
import com.kitakkun.jetwhale.debugger.host.architecture.EventFlow
import com.kitakkun.jetwhale.debugger.host.model.PluginMetaData
import com.kitakkun.jetwhale.debugger.host.settings.SettingsScreenContext
import com.kitakkun.jetwhale.debugger.host.settings.component.PluginInfoUiState
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

    EventEffect(eventFlow) { event ->
        when (event) {
            is PluginSettingsScreenEvent.PluginJarSelected -> {
                pluginInstallMutation.mutate(event.path)
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
