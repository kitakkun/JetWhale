package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.mcp.McpServerService
import com.kitakkun.jetwhale.host.model.McpCapablePluginsSubscriptionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultMcpCapablePluginsSubscriptionKey(
    private val mcpServerService: McpServerService,
) : McpCapablePluginsSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("mcp_capable_plugins"),
    subscribe = { mcpServerService.mcpCapablePluginsFlow },
)
