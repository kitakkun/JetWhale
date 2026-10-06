package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.AdbAutoPortMappingMutationKey
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class)
@Inject
class DefaultAdbAutoPortMappingMutationKey(
    private val debuggerSettingsRepository: DebuggerSettingsRepository,
) : AdbAutoPortMappingMutationKey by buildMutationKey(
    id = MutationId("adb_auto_port_mapping"),
    mutate = { isEnabled: Boolean ->
        debuggerSettingsRepository.updateAdbAutoPortMappingEnabled(enabled = isEnabled)
    },
)
