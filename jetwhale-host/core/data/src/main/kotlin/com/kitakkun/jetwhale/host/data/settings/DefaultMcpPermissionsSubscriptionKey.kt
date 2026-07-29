package com.kitakkun.jetwhale.host.data.settings

import com.kitakkun.jetwhale.host.model.McpPermissionsRepository
import com.kitakkun.jetwhale.host.model.McpPermissionsSubscriptionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultMcpPermissionsSubscriptionKey(
    private val mcpPermissionsRepository: McpPermissionsRepository,
) : McpPermissionsSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("mcp_permissions"),
    subscribe = { mcpPermissionsRepository.permissionsFlow },
)
