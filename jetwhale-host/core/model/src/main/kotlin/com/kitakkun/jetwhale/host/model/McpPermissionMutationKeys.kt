package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

data class McpHostGroupPermissionParams(val group: McpHostToolGroup, val allowed: Boolean)

data class McpPluginPermissionParams(val pluginId: String, val allowed: Boolean)

data class McpPluginToolPermissionParams(val toolName: String, val allowed: Boolean)

typealias McpHostGroupPermissionMutationKey = MutationKey<Unit, McpHostGroupPermissionParams>

typealias McpPluginToolPermissionMutationKey = MutationKey<Unit, McpPluginToolPermissionParams>

/** Separate interfaces rather than two typealiases: both carry [McpPluginPermissionParams], and to Metro a typealias is the type it aliases. */
interface McpPluginInspectPermissionMutationKey : MutationKey<Unit, McpPluginPermissionParams>

interface McpPluginInteractPermissionMutationKey : MutationKey<Unit, McpPluginPermissionParams>
