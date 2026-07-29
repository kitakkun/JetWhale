package com.kitakkun.jetwhale.host.data.settings

import com.kitakkun.jetwhale.host.model.McpHostGroupPermissionMutationKey
import com.kitakkun.jetwhale.host.model.McpHostGroupPermissionParams
import com.kitakkun.jetwhale.host.model.McpPermissionsRepository
import com.kitakkun.jetwhale.host.model.McpPluginInspectPermissionMutationKey
import com.kitakkun.jetwhale.host.model.McpPluginInteractPermissionMutationKey
import com.kitakkun.jetwhale.host.model.McpPluginPermissionParams
import com.kitakkun.jetwhale.host.model.McpPluginToolPermissionMutationKey
import com.kitakkun.jetwhale.host.model.McpPluginToolPermissionParams
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
@ContributesBinding(AppScope::class)
class DefaultMcpPluginToolPermissionMutationKey(
    private val repository: McpPermissionsRepository,
) : McpPluginToolPermissionMutationKey by buildMutationKey(
    id = MutationId("mcp_permission_plugin_tool"),
    mutate = { params: McpPluginToolPermissionParams ->
        repository.setPluginToolAllowed(params.toolName, params.allowed)
    },
)

@Inject
@ContributesBinding(AppScope::class, binding<McpPluginInspectPermissionMutationKey>())
class DefaultMcpPluginInspectPermissionMutationKey(
    private val repository: McpPermissionsRepository,
) : McpPluginInspectPermissionMutationKey,
    MutationKey<Unit, McpPluginPermissionParams> by buildMutationKey(
        id = MutationId("mcp_permission_plugin_inspect"),
        mutate = { params: McpPluginPermissionParams ->
            repository.setPluginInspectAllowed(params.pluginId, params.allowed)
        },
    )

@Inject
@ContributesBinding(AppScope::class, binding<McpPluginInteractPermissionMutationKey>())
class DefaultMcpPluginInteractPermissionMutationKey(
    private val repository: McpPermissionsRepository,
) : McpPluginInteractPermissionMutationKey,
    MutationKey<Unit, McpPluginPermissionParams> by buildMutationKey(
        id = MutationId("mcp_permission_plugin_interact"),
        mutate = { params: McpPluginPermissionParams ->
            repository.setPluginInteractAllowed(params.pluginId, params.allowed)
        },
    )
