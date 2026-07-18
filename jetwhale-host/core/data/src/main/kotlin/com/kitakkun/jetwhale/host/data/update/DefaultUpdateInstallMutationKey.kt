package com.kitakkun.jetwhale.host.data.update

import com.kitakkun.jetwhale.host.model.UpdateInstallMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultUpdateInstallMutationKey(
    private val updateCheckService: UpdateCheckService,
) : UpdateInstallMutationKey by buildMutationKey(
    id = MutationId("updateInstall"),
    mutate = { updateCheckService.triggerUpdateInstall() },
)
