package com.kitakkun.jetwhale.host.data.session

import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.DebugSessionsSubscriptionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@ContributesBinding(AppScope::class)
@Inject
class DefaultDebugSessionsSubscriptionKey(
    private val debugSessionRepository: DebugSessionRepository,
) : DebugSessionsSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("DefaultDebugSessionsSubscriptionKey"),
    subscribe = { debugSessionRepository.debugSessionsFlow },
)
