package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.data.settings.DefaultDebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.AdbAutoPortMappingMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class)
@Inject
class DefaultAdbAutoPortMappingMutationKey(
    private val settingsDataStore: DefaultDebuggerSettingsRepository,
) : AdbAutoPortMappingMutationKey by buildMutationKey(
    id = MutationId("adb_auto_port_mapping"),
    mutate = { isEnabled: Boolean ->
        settingsDataStore.updateAdbAutoPortMappingEnabled(enabled = isEnabled)
    }
)
