package com.kitakkun.jetwhale.host.data.window

import com.kitakkun.jetwhale.host.model.SidebarWidth
import com.kitakkun.jetwhale.host.model.SidebarWidthSubscriptionKey
import com.kitakkun.jetwhale.host.model.WindowStateRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.map
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@ContributesBinding(AppScope::class)
@Inject
class DefaultSidebarWidthSubscriptionKey(
    private val windowStateRepository: WindowStateRepository,
) : SidebarWidthSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("sidebar_width"),
    subscribe = { windowStateRepository.sidebarWidthFlow.map(::SidebarWidth) },
)
