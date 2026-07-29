package com.kitakkun.jetwhale.host.data.settings

import com.kitakkun.jetwhale.host.model.McpHostGroupPermissionMutationKey
import com.kitakkun.jetwhale.host.model.McpHostGroupPermissionParams
import com.kitakkun.jetwhale.host.model.McpPermissionsRepository
import com.kitakkun.jetwhale.host.model.McpPluginOwnToolsPermissionMutationKey
import com.kitakkun.jetwhale.host.model.McpPluginPermissionParams
import com.kitakkun.jetwhale.host.model.McpPluginUiPermissionMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.buildMutationKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultMcpHostGroupPermissionMutationKey(
    private val repository: McpPermissionsRepository,
) : McpHostGroupPermissionMutationKey by buildMutationKey(
    id = MutationId("mcp_permission_host_group"),
    mutate = { params: McpHostGroupPermissionParams ->
        repository.setHostGroupAllowed(params.group, params.allowed)
    },
)

@Inject
@ContributesBinding(AppScope::class, binding<McpPluginUiPermissionMutationKey>())
class DefaultMcpPluginUiPermissionMutationKey(
    private val repository: McpPermissionsRepository,
) : McpPluginUiPermissionMutationKey,
    MutationKey<Unit, McpPluginPermissionParams> by buildMutationKey(
        id = MutationId("mcp_permission_plugin_ui"),
        mutate = { params: McpPluginPermissionParams ->
            repository.setPluginUiAllowed(params.pluginId, params.allowed)
        },
    )

@Inject
@ContributesBinding(AppScope::class, binding<McpPluginOwnToolsPermissionMutationKey>())
class DefaultMcpPluginOwnToolsPermissionMutationKey(
    private val repository: McpPermissionsRepository,
) : McpPluginOwnToolsPermissionMutationKey,
    MutationKey<Unit, McpPluginPermissionParams> by buildMutationKey(
        id = MutationId("mcp_permission_plugin_own_tools"),
        mutate = { params: McpPluginPermissionParams ->
            repository.setPluginOwnToolsAllowed(params.pluginId, params.allowed)
        },
    )
