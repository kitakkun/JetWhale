package com.kitakkun.jetwhale.host.data.update

import com.kitakkun.jetwhale.host.model.CancelHostUpdateDownloadMutationKey
import com.kitakkun.jetwhale.host.model.HostUpdateService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class, binding<CancelHostUpdateDownloadMutationKey>())
@Inject
class DefaultCancelHostUpdateDownloadMutationKey(
    private val hostUpdateService: HostUpdateService,
) : CancelHostUpdateDownloadMutationKey,
    MutationKey<Unit, Unit> by buildMutationKey(
        id = MutationId("cancel_host_update_download"),
        mutate = { hostUpdateService.cancelDownload() },
    )
