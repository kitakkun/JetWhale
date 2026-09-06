package com.kitakkun.jetwhale.host.settings.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
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
    val tree = buildPermissionTree(
        uiState = uiState,
        onSetHostGroupAllowed = onSetHostGroupAllowed,
        onSetPluginInspectAllowed = onSetPluginInspectAllowed,
        onSetPluginInteractAllowed = onSetPluginInteractAllowed,
        onSetPluginToolAllowed = onSetPluginToolAllowed,
    )
    // Keyed by node id rather than by position, so a plugin appearing or disappearing does not hand
    // its expansion state to whichever node took its place.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    // The heading comes from the SettingOptionView this sits in, like every other settings block.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        JwText(
            text = stringResource(Res.string.mcp_permission_note),
            style = JwTheme.textStyles.bodySmall,
            color = JwTheme.colors.textSecondary,
        )
        tree.forEach { node ->
            PermissionNodeView(node = node, depth = 0, expanded = expanded)
        }
    }
}

/**
 * A node in the permission tree.
 *
 * The tree is built up front rather than emitted row by row so that a branch can count what it
 * contains before drawing itself: both the summary and the tri-state need the whole subtree, at any
 * depth.
 */
private sealed interface PermissionNode {
    val id: String
    val label: String

    data class Leaf(
        override val id: String,
        override val label: String,
        val allowed: Boolean,
        val onSetAllowed: (Boolean) -> Unit,
    ) : PermissionNode

    data class Branch(
        override val id: String,
        override val label: String,
        val children: List<PermissionNode>,
        val startExpanded: Boolean,
        /** Shown in place of the children when there are none. */
        val emptyHint: String? = null,
    ) : PermissionNode
}

private val PermissionNode.leaves: List<PermissionNode.Leaf>
    get() = when (this) {
        is PermissionNode.Leaf -> listOf(this)
        is PermissionNode.Branch -> children.flatMap { it.leaves }
    }

@Composable
private fun buildPermissionTree(
    uiState: McpPermissionsUiState,
    onSetHostGroupAllowed: (McpHostToolGroup, Boolean) -> Unit,
    onSetPluginInspectAllowed: (pluginId: String, allowed: Boolean) -> Unit,
    onSetPluginInteractAllowed: (pluginId: String, allowed: Boolean) -> Unit,
    onSetPluginToolAllowed: (toolName: String, allowed: Boolean) -> Unit,
): List<PermissionNode> = listOf(
    PermissionNode.Branch(
        id = "host",
        label = stringResource(Res.string.mcp_permission_host_label),
        startExpanded = true,
        children = McpHostToolGroup.entries.map { group ->
            PermissionNode.Leaf(
                id = "host/${group.name}",
                label = stringResource(group.labelResource()),
                allowed = group in uiState.allowedHostGroups,
                onSetAllowed = { onSetHostGroupAllowed(group, it) },
            )
        },
    ),
    PermissionNode.Branch(
        id = "plugins",
        label = stringResource(Res.string.mcp_permission_plugins_label),
        startExpanded = true,
        emptyHint = stringResource(Res.string.mcp_permission_no_plugins),
        children = uiState.plugins.map { plugin ->
            PermissionNode.Branch(
                id = "plugin/${plugin.pluginId}",
                label = plugin.displayName,
                // Collapsed by default: with a few plugins installed the fully expanded tree is
                // longer than the pane, and the summary already says whether this one needs opening.
                startExpanded = false,
                children = listOf(
                    PermissionNode.Branch(
                        id = "plugin/${plugin.pluginId}/ui",
                        label = stringResource(Res.string.mcp_permission_plugin_ui),
                        startExpanded = true,
                        children = listOf(
                            PermissionNode.Leaf(
                                id = "plugin/${plugin.pluginId}/ui/inspect",
                                label = stringResource(Res.string.mcp_permission_plugin_inspect),
                                allowed = plugin.inspectAllowed,
                                onSetAllowed = { onSetPluginInspectAllowed(plugin.pluginId, it) },
                            ),
                            PermissionNode.Leaf(
                                id = "plugin/${plugin.pluginId}/ui/interact",
                                label = stringResource(Res.string.mcp_permission_plugin_interact),
                                allowed = plugin.interactAllowed,
                                onSetAllowed = { onSetPluginInteractAllowed(plugin.pluginId, it) },
                            ),
                        ),
                    ),
                    PermissionNode.Branch(
                        id = "plugin/${plugin.pluginId}/tools",
                        label = stringResource(Res.string.mcp_permission_plugin_own_tools),
                        startExpanded = true,
                        // Its commands are only published once it is instantiated for a session, so
                        // with nothing connected there is no list to show. Stored denials survive.
                        emptyHint = stringResource(Res.string.mcp_permission_plugin_tools_offline),
                        children = plugin.tools.map { tool ->
                            PermissionNode.Leaf(
                                id = "tool/${tool.toolName}",
                                label = tool.toolName,
                                allowed = tool.allowed,
                                onSetAllowed = { onSetPluginToolAllowed(tool.toolName, it) },
                            )
                        },
                    ),
                ),
            )
        },
    ),
)

private val INDENT_STEP = 24.dp

@Composable
private fun PermissionNodeView(
    node: PermissionNode,
    depth: Int,
    expanded: MutableMap<String, Boolean>,
) {
    when (node) {
        is PermissionNode.Leaf -> LeafRow(node = node, depth = depth)

        is PermissionNode.Branch -> {
            val isExpanded = expanded[node.id] ?: node.startExpanded
            BranchRow(
                node = node,
                depth = depth,
                isExpanded = isExpanded,
                onToggleExpanded = { expanded[node.id] = !isExpanded },
            )
            if (isExpanded) {
                if (node.children.isEmpty() && node.emptyHint != null) {
                    JwText(
                        text = node.emptyHint,
                        style = JwTheme.textStyles.bodySmall,
                        color = JwTheme.colors.textSecondary,
                        modifier = Modifier.padding(start = INDENT_STEP * (depth + 1)),
                    )
                }
                node.children.forEach { child ->
                    PermissionNodeView(node = child, depth = depth + 1, expanded = expanded)
                }
            }
        }
    }
}

@Composable
private fun BranchRow(
    node: PermissionNode.Branch,
    depth: Int,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val leaves = node.leaves
    val allowedCount = leaves.count { it.allowed }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = INDENT_STEP * depth),
    ) {
        // The tri-state is a summary of the leaves rather than a switch of its own, so a half-ticked
        // parent is the honest rendering of a partial selection.
        TriStateCheckbox(
            state = toggleStateOf(leaves.map { it.allowed }),
            enabled = leaves.isNotEmpty(),
            // A partially-ticked parent turns everything on: the alternative — clearing a mixed
            // selection — throws away choices the user made one by one.
            onClick = {
                val allowAll = allowedCount < leaves.size
                leaves.forEach { it.onSetAllowed(allowAll) }
            },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToggleExpanded),
        ) {
            JwText(
                text = node.label,
                style = JwTheme.textStyles.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            // Only while collapsed: with the children on screen the count is already there to read,
            // and a number repeating them invites being clicked as if it were a control.
            if (!isExpanded && leaves.isNotEmpty()) {
                JwText(
                    text = "$allowedCount / ${leaves.size}",
                    style = JwTheme.textStyles.label,
                    color = JwTheme.colors.textSecondary,
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun LeafRow(node: PermissionNode.Leaf, depth: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = INDENT_STEP * depth),
    ) {
        Checkbox(checked = node.allowed, onCheckedChange = node.onSetAllowed)
        JwText(
            text = node.label,
            style = JwTheme.textStyles.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** An empty subtree reads as Off rather than On: nothing is allowed there, because nothing is there. */
private fun toggleStateOf(children: List<Boolean>): ToggleableState = when {
    children.isEmpty() || children.none { it } -> ToggleableState.Off
    children.all { it } -> ToggleableState.On
    else -> ToggleableState.Indeterminate
}

private fun McpHostToolGroup.labelResource() = when (this) {
    McpHostToolGroup.OBSERVE -> Res.string.mcp_permission_group_observe
    McpHostToolGroup.NAVIGATE -> Res.string.mcp_permission_group_navigate
    McpHostToolGroup.MANAGE_PLUGINS -> Res.string.mcp_permission_group_manage_plugins
    McpHostToolGroup.SETTINGS_AND_SERVERS -> Res.string.mcp_permission_group_settings_and_servers
}
