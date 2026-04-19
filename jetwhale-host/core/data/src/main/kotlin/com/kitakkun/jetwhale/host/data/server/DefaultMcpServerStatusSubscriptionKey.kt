package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.mcp.McpServerService
import com.kitakkun.jetwhale.host.model.McpServerStatusSubscriptionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultMcpServerStatusSubscriptionKey(
    private val mcpServerService: McpServerService,
) : McpServerStatusSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("mcp_server_status"),
    subscribe = { mcpServerService.statusFlow },
)
