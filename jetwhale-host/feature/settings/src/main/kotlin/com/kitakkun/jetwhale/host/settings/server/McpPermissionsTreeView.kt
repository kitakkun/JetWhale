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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.McpHostToolGroup
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.mcp_permission_group_manage_plugins
import com.kitakkun.jetwhale.host.settings.mcp_permission_group_navigate
import com.kitakkun.jetwhale.host.settings.mcp_permission_group_observe
import com.kitakkun.jetwhale.host.settings.mcp_permission_group_settings_and_servers
import com.kitakkun.jetwhale.host.settings.mcp_permission_host_label
import com.kitakkun.jetwhale.host.settings.mcp_permission_launch_override_note
import com.kitakkun.jetwhale.host.settings.mcp_permission_no_plugins
import com.kitakkun.jetwhale.host.settings.mcp_permission_note
import com.kitakkun.jetwhale.host.settings.mcp_permission_plugin_inspect
import com.kitakkun.jetwhale.host.settings.mcp_permission_plugin_interact
import com.kitakkun.jetwhale.host.settings.mcp_permission_plugin_own_tools
import com.kitakkun.jetwhale.host.settings.mcp_permission_plugin_tools_offline
import com.kitakkun.jetwhale.host.settings.mcp_permission_plugin_ui
import com.kitakkun.jetwhale.host.settings.mcp_permission_plugins_label
import com.kitakkun.jetwhale.host.ui.JwCheckbox
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTriStateCheckbox
import org.jetbrains.compose.resources.stringResource

/** One tool a plugin contributes, permitted on its own. */
data class McpPluginToolUiState(
    val toolName: String,
    val allowed: Boolean,
)

/**
 * @property tools Empty when the plugin has no live instance — it only publishes its commands once
 * instantiated.
 */
data class McpPluginPermissionUiState(
    val pluginId: String,
    val displayName: String,
    val inspectAllowed: Boolean,
    val interactAllowed: Boolean,
    val tools: List<McpPluginToolUiState>,
)

/**
 * @property isOverriddenForLaunch True while a launch flag allows every tool. The tree then shows
 * what the launch actually grants and takes no input: a stored choice cannot win against the
 * override for this process, so an editable box here would swallow the click and change nothing on
 * screen.
 */
data class McpPermissionsUiState(
    val allowedHostGroups: Set<McpHostToolGroup>,
    val plugins: List<McpPluginPermissionUiState>,
    val isOverriddenForLaunch: Boolean,
)

@Composable
fun McpPermissionsTreeView(
    uiState: McpPermissionsUiState,
    onSetHostGroupAllowed: (McpHostToolGroup, Boolean) -> Unit,
    onSetPluginInspectAllowed: (pluginId: String, allowed: Boolean) -> Unit,
    onSetPluginInteractAllowed: (pluginId: String, allowed: Boolean) -> Unit,
    onSetPluginToolAllowed: (toolName: String, allowed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tree = buildPermissionTree(uiState = uiState)
    // Keyed by node id rather than by position, so a plugin appearing or disappearing does not hand
    // its expansion state to whichever node took its place.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    // The heading comes from the SettingOptionView this sits in, like every other settings block.
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        JwText(
            text = stringResource(Res.string.mcp_permission_note),
            style = JwTheme.textStyles.bodySmall,
            color = JwTheme.colors.textSecondary,
        )
        if (uiState.isOverriddenForLaunch) {
            JwText(
                text = stringResource(Res.string.mcp_permission_launch_override_note),
                style = JwTheme.textStyles.bodySmall,
                color = JwTheme.colors.warning,
            )
        }
        tree.forEach { node ->
            PermissionNodeView(
                node = node,
                depth = 0,
                expanded = expanded,
                enabled = !uiState.isOverriddenForLaunch,
                onSetAllowed = { target, allowed ->
                    when (target) {
                        is PermissionTarget.HostGroup -> onSetHostGroupAllowed(target.group, allowed)
                        is PermissionTarget.PluginInspect -> onSetPluginInspectAllowed(target.pluginId, allowed)
                        is PermissionTarget.PluginInteract -> onSetPluginInteractAllowed(target.pluginId, allowed)
                        is PermissionTarget.PluginTool -> onSetPluginToolAllowed(target.toolName, allowed)
                    }
                },
            )
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
    val label: String

    data class Leaf(
        override val label: String,
        val target: PermissionTarget,
        val allowed: Boolean,
    ) : PermissionNode

    /** @property emptyHint Shown in place of the children when there are none. */
    data class Branch(
        val id: String,
        override val label: String,
        val children: List<PermissionNode>,
        val startExpanded: Boolean,
        val emptyHint: String? = null,
    ) : PermissionNode
}

/** What a leaf's box permits, so that a tick can be reported without the tree holding a callback. */
private sealed interface PermissionTarget {
    data class HostGroup(val group: McpHostToolGroup) : PermissionTarget
    data class PluginInspect(val pluginId: String) : PermissionTarget
    data class PluginInteract(val pluginId: String) : PermissionTarget
    data class PluginTool(val toolName: String) : PermissionTarget
}

private val PermissionNode.leaves: List<PermissionNode.Leaf>
    get() = when (this) {
        is PermissionNode.Leaf -> listOf(this)
        is PermissionNode.Branch -> children.flatMap(PermissionNode::leaves)
    }

@Composable
private fun buildPermissionTree(uiState: McpPermissionsUiState): List<PermissionNode> = listOf(
    PermissionNode.Branch(
        id = "host",
        label = stringResource(Res.string.mcp_permission_host_label),
        startExpanded = true,
        children = McpHostToolGroup.entries.map { group ->
            PermissionNode.Leaf(
                label = stringResource(group.labelResource()),
                target = PermissionTarget.HostGroup(group),
                allowed = group in uiState.allowedHostGroups,
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
                                label = stringResource(Res.string.mcp_permission_plugin_inspect),
                                target = PermissionTarget.PluginInspect(plugin.pluginId),
                                allowed = plugin.inspectAllowed,
                            ),
                            PermissionNode.Leaf(
                                label = stringResource(Res.string.mcp_permission_plugin_interact),
                                target = PermissionTarget.PluginInteract(plugin.pluginId),
                                allowed = plugin.interactAllowed,
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
                                label = tool.toolName,
                                target = PermissionTarget.PluginTool(tool.toolName),
                                allowed = tool.allowed,
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
    enabled: Boolean,
    onSetAllowed: (PermissionTarget, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (node) {
        is PermissionNode.Leaf -> LeafRow(
            node = node,
            depth = depth,
            enabled = enabled,
            onSetAllowed = onSetAllowed,
            modifier = modifier,
        )

        is PermissionNode.Branch -> {
            val isExpanded = expanded[node.id] ?: node.startExpanded
            BranchRow(
                node = node,
                depth = depth,
                isExpanded = isExpanded,
                enabled = enabled,
                onToggleExpanded = { expanded[node.id] = !isExpanded },
                onSetAllowed = onSetAllowed,
                modifier = modifier,
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
                    PermissionNodeView(
                        node = child,
                        depth = depth + 1,
                        expanded = expanded,
                        enabled = enabled,
                        onSetAllowed = onSetAllowed,
                    )
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
    enabled: Boolean,
    onToggleExpanded: () -> Unit,
    onSetAllowed: (PermissionTarget, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val leaves = node.leaves
    val allowedCount = leaves.count(PermissionNode.Leaf::allowed)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = INDENT_STEP * depth),
    ) {
        // The tri-state is a summary of the leaves rather than a switch of its own, so a half-ticked
        // parent is the honest rendering of a partial selection.
        JwTriStateCheckbox(
            state = toggleStateOf(leaves.map(PermissionNode.Leaf::allowed)),
            label = null,
            enabled = enabled && leaves.isNotEmpty(),
            // A partially-ticked parent turns everything on: the alternative — clearing a mixed
            // selection — throws away choices the user made one by one.
            onClick = {
                val allowAll = allowedCount < leaves.size
                leaves.forEach { onSetAllowed(it.target, allowAll) }
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
            JwIcon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = JwTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun LeafRow(
    node: PermissionNode.Leaf,
    depth: Int,
    enabled: Boolean,
    onSetAllowed: (PermissionTarget, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    JwCheckbox(
        checked = node.allowed,
        onCheckedChange = { onSetAllowed(node.target, it) },
        label = node.label,
        enabled = enabled,
        modifier = modifier.padding(start = INDENT_STEP * depth),
    )
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

@Preview
@Composable
private fun McpPermissionsTreeViewPreview() {
    JwTheme(darkTheme = false) {
        McpPermissionsTreeView(
            uiState = McpPermissionsUiState(
                allowedHostGroups = setOf(McpHostToolGroup.OBSERVE, McpHostToolGroup.NAVIGATE),
                plugins = listOf(
                    McpPluginPermissionUiState(
                        pluginId = "com.example.inspector",
                        displayName = "Inspector",
                        inspectAllowed = true,
                        interactAllowed = false,
                        tools = listOf(
                            McpPluginToolUiState(toolName = "inspector.listNodes", allowed = true),
                            McpPluginToolUiState(toolName = "inspector.clearNodes", allowed = false),
                        ),
                    ),
                ),
                isOverriddenForLaunch = false,
            ),
            onSetHostGroupAllowed = { _, _ -> },
            onSetPluginInspectAllowed = { _, _ -> },
            onSetPluginInteractAllowed = { _, _ -> },
            onSetPluginToolAllowed = { _, _ -> },
        )
    }
}
