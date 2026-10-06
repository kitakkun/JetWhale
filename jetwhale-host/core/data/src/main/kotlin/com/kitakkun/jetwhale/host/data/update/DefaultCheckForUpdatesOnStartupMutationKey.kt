package com.kitakkun.jetwhale.host.data.update

import com.kitakkun.jetwhale.host.model.CheckForUpdatesOnStartupMutationKey
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class, binding<CheckForUpdatesOnStartupMutationKey>())
@Inject
class DefaultCheckForUpdatesOnStartupMutationKey(
    private val settingsRepository: DebuggerSettingsRepository,
) : CheckForUpdatesOnStartupMutationKey,
    MutationKey<Unit, Boolean> by buildMutationKey(
        id = MutationId("checkForUpdatesOnStartup"),
        mutate = { enabled: Boolean ->
            settingsRepository.updateCheckForUpdatesOnStartup(enabled)
        },
    )
