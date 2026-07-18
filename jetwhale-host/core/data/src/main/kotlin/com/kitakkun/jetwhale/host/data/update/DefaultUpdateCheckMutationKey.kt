package com.kitakkun.jetwhale.host.data.update

import com.kitakkun.jetwhale.host.model.UpdateCheckMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultUpdateCheckMutationKey(
    private val updateCheckService: UpdateCheckService,
) : UpdateCheckMutationKey by buildMutationKey(
    id = MutationId("updateCheck"),
    mutate = { updateCheckService.checkForUpdates() },
)
