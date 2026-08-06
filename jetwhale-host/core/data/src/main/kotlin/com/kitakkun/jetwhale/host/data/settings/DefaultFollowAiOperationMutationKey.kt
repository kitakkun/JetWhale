package com.kitakkun.jetwhale.host.data.settings

import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.FollowAiOperationMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class, binding<FollowAiOperationMutationKey>())
@Inject
class DefaultFollowAiOperationMutationKey(
    private val settingsRepository: DebuggerSettingsRepository,
) : FollowAiOperationMutationKey,
    MutationKey<Unit, Boolean> by buildMutationKey(
        id = MutationId("followAiOperation"),
        mutate = { enabled: Boolean ->
            settingsRepository.updateFollowAiOperationEnabled(enabled)
        },
    )
