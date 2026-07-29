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
import androidx.compose.ui.text.style.TextOverflow
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
import com.kitakkun.jetwhale.host.settings.mcp_permission_plugin_inspect
import com.kitakkun.jetwhale.host.settings.mcp_permission_plugin_interact
import com.kitakkun.jetwhale.host.settings.mcp_permission_plugin_own_tools
import com.kitakkun.jetwhale.host.settings.mcp_permission_plugin_tools_offline
import com.kitakkun.jetwhale.host.settings.mcp_permission_plugin_ui
import com.kitakkun.jetwhale.host.settings.mcp_permission_plugins_label
import org.jetbrains.compose.resources.stringResource

/** One tool a plugin contributes, permitted on its own. */
data class McpPluginToolUiState(
    val toolName: String,
    val allowed: Boolean,
)

data class McpPluginPermissionUiState(
    val pluginId: String,
    val displayName: String,
    val inspectAllowed: Boolean,
    val interactAllowed: Boolean,
    /** Empty when the plugin has no live instance — it only publishes its commands once instantiated. */
    val tools: List<McpPluginToolUiState>,
)

data class McpPermissionsUiState(
    val allowedHostGroups: Set<McpHostToolGroup>,
    val plugins: List<McpPluginPermissionUiState>,
)

@Composable
fun McpPermissionsTreeView(
    uiState: McpPermissionsUiState,
    onSetHostGroupAllowed: (McpHostToolGroup, Boolean) -> Unit,
    onSetPluginInspectAllowed: (pluginId: String, allowed: Boolean) -> Unit,
    onSetPluginInteractAllowed: (pluginId: String, allowed: Boolean) -> Unit,
    onSetPluginToolAllowed: (toolName: String, allowed: Boolean) -> Unit,
) {
    // The heading comes from the SettingOptionView this sits in, like every other settings block.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            state = toggleStateOf(uiState.plugins.flatMap { it.allLeaves() }),
            // Nothing to toggle with no plugins installed, so the parent does not pretend otherwise.
            enabled = uiState.plugins.isNotEmpty(),
            onClick = { allowAll -> uiState.plugins.forEach { it.setAll(allowAll, onSetPluginInspectAllowed, onSetPluginInteractAllowed, onSetPluginToolAllowed) } },
        )
        if (uiState.plugins.isEmpty()) {
            Hint(stringResource(Res.string.mcp_permission_no_plugins), indent = 1)
        }
        uiState.plugins.forEach { plugin ->
            ParentRow(
                label = plugin.displayName,
                state = toggleStateOf(plugin.allLeaves()),
                onClick = { allowAll -> plugin.setAll(allowAll, onSetPluginInspectAllowed, onSetPluginInteractAllowed, onSetPluginToolAllowed) },
                indent = 1,
            )

            ParentRow(
                label = stringResource(Res.string.mcp_permission_plugin_ui),
                state = toggleStateOf(listOf(plugin.inspectAllowed, plugin.interactAllowed)),
                onClick = { allowAll ->
                    onSetPluginInspectAllowed(plugin.pluginId, allowAll)
                    onSetPluginInteractAllowed(plugin.pluginId, allowAll)
                },
                indent = 2,
            )
            LeafRow(
                label = stringResource(Res.string.mcp_permission_plugin_inspect),
                checked = plugin.inspectAllowed,
                onCheckedChange = { onSetPluginInspectAllowed(plugin.pluginId, it) },
                indent = 3,
            )
            LeafRow(
                label = stringResource(Res.string.mcp_permission_plugin_interact),
                checked = plugin.interactAllowed,
                onCheckedChange = { onSetPluginInteractAllowed(plugin.pluginId, it) },
                indent = 3,
            )

            ParentRow(
                label = stringResource(Res.string.mcp_permission_plugin_own_tools),
                state = toggleStateOf(plugin.tools.map { it.allowed }),
                enabled = plugin.tools.isNotEmpty(),
                onClick = { allowAll -> plugin.tools.forEach { onSetPluginToolAllowed(it.toolName, allowAll) } },
                indent = 2,
            )
            if (plugin.tools.isEmpty()) {
                // Its commands are only published once it is instantiated for a session, so with
                // nothing connected there is no list to show. Stored denials are unaffected.
                Hint(stringResource(Res.string.mcp_permission_plugin_tools_offline), indent = 3)
            }
            plugin.tools.forEach { tool ->
                LeafRow(
                    label = tool.toolName,
                    checked = tool.allowed,
                    onCheckedChange = { onSetPluginToolAllowed(tool.toolName, it) },
                    indent = 3,
                )
            }
        }
    }
}

private fun McpPluginPermissionUiState.allLeaves(): List<Boolean> = listOf(inspectAllowed, interactAllowed) + tools.map { it.allowed }

private fun McpPluginPermissionUiState.setAll(
    allowed: Boolean,
    onSetInspect: (String, Boolean) -> Unit,
    onSetInteract: (String, Boolean) -> Unit,
    onSetTool: (String, Boolean) -> Unit,
) {
    onSetInspect(pluginId, allowed)
    onSetInteract(pluginId, allowed)
    tools.forEach { onSetTool(it.toolName, allowed) }
}

private val INDENT_STEP = 24.dp

/** An empty subtree reads as Off rather than On: nothing is allowed there, because nothing is there. */
private fun toggleStateOf(children: List<Boolean>): ToggleableState = when {
    children.isEmpty() || children.none { it } -> ToggleableState.Off
    children.all { it } -> ToggleableState.On
    else -> ToggleableState.Indeterminate
}

@Composable
private fun Hint(text: String, indent: Int) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = INDENT_STEP * indent),
    )
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
        Text(text = label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Text(text = label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun McpHostToolGroup.labelResource() = when (this) {
    McpHostToolGroup.OBSERVE -> Res.string.mcp_permission_group_observe
    McpHostToolGroup.NAVIGATE -> Res.string.mcp_permission_group_navigate
    McpHostToolGroup.MANAGE_PLUGINS -> Res.string.mcp_permission_group_manage_plugins
    McpHostToolGroup.SETTINGS_AND_SERVERS -> Res.string.mcp_permission_group_settings_and_servers
}
