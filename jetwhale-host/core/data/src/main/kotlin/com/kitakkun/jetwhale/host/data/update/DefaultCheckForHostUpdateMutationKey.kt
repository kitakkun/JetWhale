package com.kitakkun.jetwhale.host.data.update

import com.kitakkun.jetwhale.host.model.CheckForHostUpdateMutationKey
import com.kitakkun.jetwhale.host.model.HostUpdateService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class, binding<CheckForHostUpdateMutationKey>())
@Inject
class DefaultCheckForHostUpdateMutationKey(
    private val hostUpdateService: HostUpdateService,
) : CheckForHostUpdateMutationKey,
    MutationKey<Unit, Unit> by buildMutationKey(
        id = MutationId("check_for_host_update"),
        mutate = { hostUpdateService.check() },
    )
