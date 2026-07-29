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
    initial: McpPermissions = McpPermissions.AllowAll,
) : McpPermissionsRepository {

    override val permissionsFlow: StateFlow<McpPermissions>
        field = MutableStateFlow(initial)

    override suspend fun setHostGroupAllowed(group: McpHostToolGroup, allowed: Boolean) {
        permissionsFlow.value = permissionsFlow.value.let {
            it.copy(allowedHostGroups = if (allowed) it.allowedHostGroups + group else it.allowedHostGroups - group)
        }
    }

    override suspend fun setPluginInspectAllowed(pluginId: String, allowed: Boolean) {
        permissionsFlow.value = permissionsFlow.value.let {
            it.copy(pluginsDeniedInspect = it.pluginsDeniedInspect.toggle(pluginId, allowed))
        }
    }

    override suspend fun setPluginInteractAllowed(pluginId: String, allowed: Boolean) {
        permissionsFlow.value = permissionsFlow.value.let {
            it.copy(pluginsDeniedInteract = it.pluginsDeniedInteract.toggle(pluginId, allowed))
        }
    }

    override suspend fun setPluginToolAllowed(toolName: String, allowed: Boolean) {
        permissionsFlow.value = permissionsFlow.value.let {
            it.copy(deniedPluginTools = it.deniedPluginTools.toggle(toolName, allowed))
        }
    }
}

/** Denials are stored, so allowing something removes it from the set rather than adding to it. */
private fun Set<String>.toggle(entry: String, allowed: Boolean) = if (allowed) this - entry else this + entry
