package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

data class McpHostGroupPermissionParams(val group: McpHostToolGroup, val allowed: Boolean)

data class McpPluginPermissionParams(val pluginId: String, val allowed: Boolean)

typealias McpHostGroupPermissionMutationKey = MutationKey<Unit, McpHostGroupPermissionParams>

/** Separate interfaces rather than two typealiases: both carry [McpPluginPermissionParams], and to Metro a typealias is the type it aliases. */
interface McpPluginUiPermissionMutationKey : MutationKey<Unit, McpPluginPermissionParams>

interface McpPluginOwnToolsPermissionMutationKey : MutationKey<Unit, McpPluginPermissionParams>
