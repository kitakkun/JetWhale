package com.kitakkun.jetwhale.host.update

import com.kitakkun.jetwhale.host.ApplicationLifecycleOwner
import com.kitakkun.jetwhale.host.model.HostUpdateService
import com.kitakkun.jetwhale.host.model.RestartToUpdateMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class, binding<RestartToUpdateMutationKey>())
@Inject
class DefaultRestartToUpdateMutationKey(
    private val hostUpdateService: HostUpdateService,
    private val applicationLifecycleOwner: ApplicationLifecycleOwner,
) : RestartToUpdateMutationKey,
    MutationKey<Unit, Unit> by buildMutationKey(
        id = MutationId("restart_to_update"),
        mutate = {
            if (hostUpdateService.startLauncherAfterExit(retryVersion = null)) applicationLifecycleOwner.shutdown()
        },
    )
