package com.kitakkun.jetwhale.host.settings.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.McpHostToolGroup
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.mcp_permission_group_manage_plugins
import com.kitakkun.jetwhale.host.settings.mcp_permission_group_navigate
import com.kitakkun.jetwhale.host.settings.mcp_permission_group_observe
import com.kitakkun.jetwhale.host.settings.mcp_permission_group_settings_and_servers
import com.kitakkun.jetwhale.host.settings.mcp_permission_host_label
import com.kitakkun.jetwhale.host.settings.mcp_permission_no_plugins
import com.kitakkun.jetwhale.host.settings.mcp_permission_note
import com.kitakkun.jetwhale.host.settings.mcp_permission_plugin_own_tools
import com.kitakkun.jetwhale.host.settings.mcp_permission_plugin_ui
import com.kitakkun.jetwhale.host.settings.mcp_permission_plugins_label
import com.kitakkun.jetwhale.host.settings.mcp_permission_title
import org.jetbrains.compose.resources.stringResource

/** One installed plugin's two permission leaves, resolved for display. */
data class McpPluginPermissionUiState(
    val pluginId: String,
    val displayName: String,
    val uiAllowed: Boolean,
    val ownToolsAllowed: Boolean,
)

data class McpPermissionsUiState(
    val allowedHostGroups: Set<McpHostToolGroup>,
    val plugins: List<McpPluginPermissionUiState>,
)

@Composable
fun McpPermissionsView(
    uiState: McpPermissionsUiState,
    onSetHostGroupAllowed: (McpHostToolGroup, Boolean) -> Unit,
    onSetPluginUiAllowed: (pluginId: String, allowed: Boolean) -> Unit,
    onSetPluginOwnToolsAllowed: (pluginId: String, allowed: Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.mcp_permission_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(Res.string.mcp_permission_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The parent is a tri-state summary rather than a switch of its own: the stored state is the
        // leaves, so a half-ticked parent is the honest rendering of a partial selection.
        ParentRow(
            label = stringResource(Res.string.mcp_permission_host_label),
            state = toggleStateOf(McpHostToolGroup.entries.map { it in uiState.allowedHostGroups }),
            onClick = { allowAll -> McpHostToolGroup.entries.forEach { onSetHostGroupAllowed(it, allowAll) } },
        )
        McpHostToolGroup.entries.forEach { group ->
            LeafRow(
                label = stringResource(group.labelResource()),
                checked = group in uiState.allowedHostGroups,
                onCheckedChange = { onSetHostGroupAllowed(group, it) },
                indent = 1,
            )
        }

        ParentRow(
            label = stringResource(Res.string.mcp_permission_plugins_label),
            state = toggleStateOf(uiState.plugins.flatMap { listOf(it.uiAllowed, it.ownToolsAllowed) }),
            // Nothing to toggle with no plugins installed, so the parent does not pretend otherwise.
            enabled = uiState.plugins.isNotEmpty(),
            onClick = { allowAll ->
                uiState.plugins.forEach {
                    onSetPluginUiAllowed(it.pluginId, allowAll)
                    onSetPluginOwnToolsAllowed(it.pluginId, allowAll)
                }
            },
        )
        if (uiState.plugins.isEmpty()) {
            Text(
                text = stringResource(Res.string.mcp_permission_no_plugins),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = INDENT_STEP),
            )
        }
        uiState.plugins.forEach { plugin ->
            ParentRow(
                label = plugin.displayName,
                state = toggleStateOf(listOf(plugin.uiAllowed, plugin.ownToolsAllowed)),
                onClick = { allowAll ->
                    onSetPluginUiAllowed(plugin.pluginId, allowAll)
                    onSetPluginOwnToolsAllowed(plugin.pluginId, allowAll)
                },
                indent = 1,
            )
            LeafRow(
                label = stringResource(Res.string.mcp_permission_plugin_ui),
                checked = plugin.uiAllowed,
                onCheckedChange = { onSetPluginUiAllowed(plugin.pluginId, it) },
                indent = 2,
            )
            LeafRow(
                label = stringResource(Res.string.mcp_permission_plugin_own_tools),
                checked = plugin.ownToolsAllowed,
                onCheckedChange = { onSetPluginOwnToolsAllowed(plugin.pluginId, it) },
                indent = 2,
            )
        }
    }
}

private val INDENT_STEP = 24.dp

/** An empty subtree reads as Off rather than On: nothing is allowed there, because nothing is there. */
private fun toggleStateOf(children: List<Boolean>): ToggleableState = when {
    children.isEmpty() || children.none { it } -> ToggleableState.Off
    children.all { it } -> ToggleableState.On
    else -> ToggleableState.Indeterminate
}

@Composable
private fun ParentRow(
    label: String,
    state: ToggleableState,
    onClick: (allowAll: Boolean) -> Unit,
    indent: Int = 0,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = INDENT_STEP * indent),
    ) {
        TriStateCheckbox(
            state = state,
            enabled = enabled,
            // A partially-ticked parent turns everything on: the alternative — clearing a mixed
            // selection — throws away choices the user made one by one.
            onClick = { onClick(state != ToggleableState.On) },
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LeafRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    indent: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = INDENT_STEP * indent),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun McpHostToolGroup.labelResource() = when (this) {
    McpHostToolGroup.OBSERVE -> Res.string.mcp_permission_group_observe
    McpHostToolGroup.NAVIGATE -> Res.string.mcp_permission_group_navigate
    McpHostToolGroup.MANAGE_PLUGINS -> Res.string.mcp_permission_group_manage_plugins
    McpHostToolGroup.SETTINGS_AND_SERVERS -> Res.string.mcp_permission_group_settings_and_servers
}
