package com.kitakkun.jetwhale.host.model

import soil.query.SubscriptionKey

/**
 * One installed plugin as the permission tree needs it: what to call it, and which of its own tools
 * can currently be listed.
 *
 * [tools] is empty for a plugin with no live instance. A plugin only publishes its commands when it
 * is instantiated for a session, so with nothing connected there is no list to show — the stored
 * denials survive regardless, being keyed by tool name.
 */
data class McpPermissionPlugin(
    val pluginId: String,
    val displayName: String,
    val tools: List<McpToolSummary>,
)

/**
 * Everything the permission tree renders, as a single snapshot.
 *
 * Combined here rather than subscribed separately by the screen: the tree is one control, and three
 * independently-arriving sources would let it draw a plugin against another plugin's tools.
 */
data class McpPermissionsView(
    val permissions: McpPermissions,
    val plugins: List<McpPermissionPlugin>,
)

typealias McpPermissionsViewSubscriptionKey = SubscriptionKey<McpPermissionsView>
