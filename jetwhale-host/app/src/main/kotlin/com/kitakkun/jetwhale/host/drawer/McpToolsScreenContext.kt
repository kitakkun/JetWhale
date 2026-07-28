package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.architecture.ScreenContext
import com.kitakkun.jetwhale.host.model.DebugSessionsSubscriptionKey
import com.kitakkun.jetwhale.host.model.LoadedPluginsMetaDataSubscriptionKey
import com.kitakkun.jetwhale.host.model.McpActivitySubscriptionKey
import com.kitakkun.jetwhale.host.model.McpCapablePluginsSubscriptionKey
import dev.zacsweers.metro.Inject

/**
 * The MCP tools browser subscribes to the whole picture itself rather than being handed one
 * plugin's slice, so it can show every session and plugin and let the user narrow it down.
 */
@Inject
class McpToolsScreenContext(
    val mcpCapablePluginsSubscriptionKey: McpCapablePluginsSubscriptionKey,
    val mcpActivitySubscriptionKey: McpActivitySubscriptionKey,
    val debugSessionsSubscriptionKey: DebugSessionsSubscriptionKey,
    val loadedPluginsMetaDataSubscriptionKey: LoadedPluginsMetaDataSubscriptionKey,
) : ScreenContext
