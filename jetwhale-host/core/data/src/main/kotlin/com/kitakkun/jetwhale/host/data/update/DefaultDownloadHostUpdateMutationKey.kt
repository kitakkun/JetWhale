package com.kitakkun.jetwhale.host.data.update

import com.kitakkun.jetwhale.host.model.DownloadHostUpdateMutationKey
import com.kitakkun.jetwhale.host.model.HostUpdateService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class, binding<DownloadHostUpdateMutationKey>())
@Inject
class DefaultDownloadHostUpdateMutationKey(
    private val hostUpdateService: HostUpdateService,
) : DownloadHostUpdateMutationKey,
    MutationKey<Unit, Unit> by buildMutationKey(
        id = MutationId("download_host_update"),
        mutate = { hostUpdateService.download() },
    )
