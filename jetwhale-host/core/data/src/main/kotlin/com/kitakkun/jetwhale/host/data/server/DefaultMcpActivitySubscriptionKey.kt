package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.McpActivityRepository
import com.kitakkun.jetwhale.host.model.McpActivitySubscriptionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultMcpActivitySubscriptionKey(
    private val mcpActivityRepository: McpActivityRepository,
) : McpActivitySubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("mcp_activity"),
    subscribe = { mcpActivityRepository.activityFlow },
)
