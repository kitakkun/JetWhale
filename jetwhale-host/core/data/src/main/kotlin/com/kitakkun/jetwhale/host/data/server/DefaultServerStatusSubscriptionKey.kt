package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.ServerStatusSubscriptionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultServerStatusSubscriptionKey(
    private val ktorWebSocketServer: KtorWebSocketServer,
) : ServerStatusSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("server_status"),
    subscribe = { ktorWebSocketServer.statusFlow },
)
