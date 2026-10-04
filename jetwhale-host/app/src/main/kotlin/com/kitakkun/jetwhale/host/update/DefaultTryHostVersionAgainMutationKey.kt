package com.kitakkun.jetwhale.host.update

import com.kitakkun.jetwhale.host.ApplicationLifecycleOwner
import com.kitakkun.jetwhale.host.model.HostUpdateService
import com.kitakkun.jetwhale.host.model.TryHostVersionAgainMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class, binding<TryHostVersionAgainMutationKey>())
@Inject
class DefaultTryHostVersionAgainMutationKey(
    private val hostUpdateService: HostUpdateService,
    private val applicationLifecycleOwner: ApplicationLifecycleOwner,
) : TryHostVersionAgainMutationKey,
    MutationKey<Unit, String> by buildMutationKey(
        id = MutationId("try_host_version_again"),
        mutate = { version: String ->
            if (hostUpdateService.startLauncherAfterExit(retryVersion = version)) applicationLifecycleOwner.shutdown()
        },
    )
