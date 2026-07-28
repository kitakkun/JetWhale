package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.close
import com.kitakkun.jetwhale.host.mcp_tool_executing
import com.kitakkun.jetwhale.host.mcp_tools_available
import com.kitakkun.jetwhale.host.mcp_tools_no_match
import com.kitakkun.jetwhale.host.mcp_tools_parameters
import com.kitakkun.jetwhale.host.mcp_tools_required
import com.kitakkun.jetwhale.host.mcp_tools_search
import com.kitakkun.jetwhale.host.model.McpToolParameterSummary
import com.kitakkun.jetwhale.host.model.McpToolSummary
import com.kitakkun.jetwhale.host.model.PluginIconResource
import com.kitakkun.jetwhale.host.puzzle_filled
import com.kitakkun.jetwhale.host.puzzle_outlined
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PluginDrawerItemView(
    enabled: Boolean,
    name: String,
    selected: Boolean,
    underAiControl: Boolean,
    mcpTools: ImmutableList<McpToolSummary>,
    activeIconResource: PluginIconResource?,
    inactiveIconResource: PluginIconResource?,
    onClick: () -> Unit,
    popupMenuContent: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        NavigationDrawerItem(
            label = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // fill = false lets a long name shrink and ellipsize instead of pushing the
                    // badge off the row.
                    Text(
                        text = name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (mcpTools.isNotEmpty()) {
                        McpBadge(operating = underAiControl, pluginName = name, tools = mcpTools)
                    }
                }
            },
            icon = {
                Icon(
                    painter = when {
                        selected && enabled -> rememberPluginIconSvgPainter(activeIconResource)
                            ?: painterResource(Res.drawable.puzzle_filled)

                        else -> rememberPluginIconSvgPainter(inactiveIconResource)
                            ?: painterResource(Res.drawable.puzzle_outlined)
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            badge = {
                popupMenuContent?.let {
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { expanded = true },
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null,
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            it({ expanded = false })
                        }
                    }
                }
            },
            selected = selected,
            onClick = onClick,
            // Because NavigationDrawerItem does not have enabled parameter,
            // we manually provide better visual feedback for non-enabled plugins
            modifier = Modifier.alpha(if (enabled) 1.0f else 0.5f),
        )
        if (underAiControl) {
            // A rotating gradient ring drawn over the item makes the plugin an agent is driving
            // unmistakable even when the list is scrolled and the label is out of view.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .aiOperatingBorder(color = AiOperatingAccentColor, width = 3.dp),
            )
        }
    }
}

/**
 * A compact "MCP" badge shown after a plugin's name when it exposes MCP tools. It is filled while an
 * agent is running one of those tools and outlined otherwise, and opens a dialog listing the tools.
 */
@Composable
private fun McpBadge(
    operating: Boolean,
    pluginName: String,
    tools: ImmutableList<McpToolSummary>,
) {
    var showDialog by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    val contentColor = if (operating) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
    val decoration = if (operating) {
        Modifier.background(AiOperatingAccentColor, shape)
    } else {
        Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, shape)
    }
    Row(
        modifier = Modifier
            .clip(shape)
            .then(decoration)
            .clickable { showDialog = true }
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "MCP",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
        // Signals that clicking opens a separate dialog.
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(11.dp),
        )
    }
    if (showDialog) {
        McpToolsDialog(
            operating = operating,
            pluginName = pluginName,
            tools = tools,
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun McpToolsDialog(
    operating: Boolean,
    pluginName: String,
    tools: ImmutableList<McpToolSummary>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier
                    .width(760.dp)
                    .height(500.dp)
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pluginName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(
                            if (operating) Res.string.mcp_tool_executing else Res.string.mcp_tools_available,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(12.dp))

                var query by remember { mutableStateOf("") }
                var selectedName by remember { mutableStateOf(tools.firstOrNull()?.name) }
                val filtered = remember(query, tools) {
                    if (query.isBlank()) {
                        tools
                    } else {
                        tools.filter {
                            it.name.contains(query, ignoreCase = true) ||
                                it.description.contains(query, ignoreCase = true)
                        }
                    }
                }
                val selected = filtered.firstOrNull { it.name == selectedName } ?: filtered.firstOrNull()

                Row(modifier = Modifier.weight(1f)) {
                    // Left pane: search + tool list.
                    Column(modifier = Modifier.width(260.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            placeholder = { Text(stringResource(Res.string.mcp_tools_search)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.size(8.dp))
                        LazyColumn(modifier = Modifier.fillMaxHeight()) {
                            items(filtered, key = { it.name }) { tool ->
                                val isSelected = tool.name == selected?.name
                                Text(
                                    text = tool.name.substringAfterLast('.'),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                        )
                                        .clickable { selectedName = tool.name }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                    VerticalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    // Right pane: the selected tool's detail.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (selected == null) {
                            Text(
                                text = stringResource(Res.string.mcp_tools_no_match),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = selected.name.substringAfterLast('.'),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                text = selected.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = selected.description,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (selected.parameters.isNotEmpty()) {
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    text = stringResource(Res.string.mcp_tools_parameters),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                selected.parameters.forEach { param -> McpParameterRow(param) }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.close))
                    }
                }
            }
        }
    }
}

@Composable
private fun McpParameterRow(param: McpToolParameterSummary) {
    Column(
        modifier = Modifier.padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = param.name,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            if (param.type.isNotEmpty()) {
                Text(
                    text = param.type,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (param.required) {
                Text(
                    text = stringResource(Res.string.mcp_tools_required),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (param.description.isNotEmpty()) {
            Text(
                text = param.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
