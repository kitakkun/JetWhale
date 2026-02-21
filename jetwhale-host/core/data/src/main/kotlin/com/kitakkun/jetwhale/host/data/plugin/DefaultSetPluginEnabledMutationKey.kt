package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.SetPluginEnabledMutationKey
import com.kitakkun.jetwhale.host.model.SetPluginEnabledParams
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class)
@Inject
class DefaultSetPluginEnabledMutationKey(
    private val enabledPluginsRepository: EnabledPluginsRepository,
) : SetPluginEnabledMutationKey by buildMutationKey(
    id = MutationId("set_plugin_enabled"),
    mutate = { params: SetPluginEnabledParams ->
        enabledPluginsRepository.setPluginEnabled(params.pluginId, params.enabled)
    }
)
