package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.McpHostToolGroup
import com.kitakkun.jetwhale.host.model.McpPermissions
import com.kitakkun.jetwhale.host.model.McpPermissionsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory permissions. Defaults to allowing everything so a test that is not about permissions
 * does not have to opt in to each group it happens to touch.
 */
class FakeMcpPermissionsRepository(
    initial: McpPermissions = AllowAll,
) : McpPermissionsRepository {

    override val permissionsFlow: StateFlow<McpPermissions>
        field = MutableStateFlow(initial)

    override suspend fun setHostGroupAllowed(group: McpHostToolGroup, allowed: Boolean) {
        permissionsFlow.value = permissionsFlow.value.let {
            it.copy(allowedHostGroups = if (allowed) it.allowedHostGroups + group else it.allowedHostGroups - group)
        }
    }

    override suspend fun setPluginUiAllowed(pluginId: String, allowed: Boolean) {
        permissionsFlow.value = permissionsFlow.value.let {
            it.copy(pluginsDeniedUi = if (allowed) it.pluginsDeniedUi - pluginId else it.pluginsDeniedUi + pluginId)
        }
    }

    override suspend fun setPluginOwnToolsAllowed(pluginId: String, allowed: Boolean) {
        permissionsFlow.value = permissionsFlow.value.let {
            it.copy(pluginsDeniedOwnTools = if (allowed) it.pluginsDeniedOwnTools - pluginId else it.pluginsDeniedOwnTools + pluginId)
        }
    }

    companion object {
        val AllowAll = McpPermissions(
            allowedHostGroups = McpHostToolGroup.entries.toSet(),
            pluginsDeniedUi = emptySet(),
            pluginsDeniedOwnTools = emptySet(),
        )
    }
}
