package com.kitakkun.jetwhale.host.data.update

import com.kitakkun.jetwhale.host.model.HostUpdateService
import com.kitakkun.jetwhale.host.model.HostUpdateStateSubscriptionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultHostUpdateStateSubscriptionKey(
    private val hostUpdateService: HostUpdateService,
) : HostUpdateStateSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("host_update_state"),
    subscribe = { hostUpdateService.stateFlow },
)
