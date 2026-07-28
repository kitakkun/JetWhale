package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.close
import com.kitakkun.jetwhale.host.mcp_history_copy_arguments
import com.kitakkun.jetwhale.host.mcp_history_copy_details
import com.kitakkun.jetwhale.host.mcp_history_copy_response
import com.kitakkun.jetwhale.host.mcp_history_copy_tool_name
import com.kitakkun.jetwhale.host.mcp_history_empty
import com.kitakkun.jetwhale.host.mcp_history_failed
import com.kitakkun.jetwhale.host.mcp_history_no_arguments
import com.kitakkun.jetwhale.host.mcp_history_no_response
import com.kitakkun.jetwhale.host.mcp_history_response
import com.kitakkun.jetwhale.host.mcp_history_succeeded
import com.kitakkun.jetwhale.host.mcp_tool_executing
import com.kitakkun.jetwhale.host.mcp_tools_available
import com.kitakkun.jetwhale.host.mcp_tools_no_match
import com.kitakkun.jetwhale.host.mcp_tools_parameters
import com.kitakkun.jetwhale.host.mcp_tools_required
import com.kitakkun.jetwhale.host.mcp_tools_search
import com.kitakkun.jetwhale.host.mcp_tools_tab_history
import com.kitakkun.jetwhale.host.mcp_tools_tab_tools
import com.kitakkun.jetwhale.host.model.McpCallRecord
import com.kitakkun.jetwhale.host.model.McpToolParameterSummary
import com.kitakkun.jetwhale.host.model.McpToolSummary
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The panes the MCP dialog can show: the tools a plugin publishes, or the calls already made. */
internal enum class McpDialogTab {
    Tools,
    History,
}

@Composable
internal fun McpToolsDialog(
    operating: Boolean,
    pluginName: String,
    tools: ImmutableList<McpToolSummary>,
    callHistory: ImmutableList<McpCallRecord>,
    runningToolName: String?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        // The platform default constrains the dialog to its content's size; tool descriptions and
        // history need the room to grow with the window instead.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier
                    // Take most of the window so long descriptions and history are readable, but
                    // stop growing past a comfortable reading width on a large display.
                    .fillMaxSize(MCP_TOOLS_DIALOG_WINDOW_FRACTION)
                    .sizeIn(
                        minWidth = 640.dp,
                        minHeight = 440.dp,
                        maxWidth = 1200.dp,
                        maxHeight = 860.dp,
                    )
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

                var selectedTab by remember { mutableStateOf(McpDialogTab.Tools) }
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    Tab(
                        selected = selectedTab == McpDialogTab.Tools,
                        onClick = { selectedTab = McpDialogTab.Tools },
                        text = { Text(stringResource(Res.string.mcp_tools_tab_tools)) },
                    )
                    Tab(
                        selected = selectedTab == McpDialogTab.History,
                        onClick = { selectedTab = McpDialogTab.History },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(stringResource(Res.string.mcp_tools_tab_history))
                                if (callHistory.isNotEmpty()) {
                                    // How many calls the history holds, so the count is visible
                                    // without opening the tab.
                                    McpToolCallCountBadge(count = callHistory.size, running = false)
                                }
                            }
                        },
                    )
                }
                Spacer(Modifier.size(12.dp))

                // Hoisted out of the pane so switching tabs and coming back keeps the search and the
                // selected tool where the user left them.
                var query by remember { mutableStateOf("") }
                var selectedName by remember { mutableStateOf(tools.firstOrNull()?.name) }

                when (selectedTab) {
                    McpDialogTab.Tools -> McpToolsPane(
                        tools = tools,
                        query = query,
                        onQueryChange = { query = it },
                        selectedToolName = selectedName,
                        onSelectTool = { selectedName = it },
                        runningToolName = runningToolName,
                        // Counts come from the retained history, so they cover the calls the
                        // dialog can actually show rather than all time.
                        callCounts = remember(callHistory) { callHistory.groupingBy { it.toolName }.eachCount() },
                        modifier = Modifier.weight(1f),
                    )

                    McpDialogTab.History -> McpCallHistoryPane(
                        callHistory = callHistory,
                        modifier = Modifier.weight(1f),
                    )
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

/**
 * Trailing badge on a tool row: the number of recorded calls, shown quietly. While an agent is
 * running the tool it takes the accent fill and the same rotating ring the drawer item uses, so
 * "being called right now" reads the same way everywhere.
 */
@Composable
internal fun McpToolCallCountBadge(count: Int, running: Boolean) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (running) AiOperatingAccentColor else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape,
            )
            .then(
                if (running) Modifier.aiOperatingBorder(color = AiOperatingAccentColor, width = 2.dp) else Modifier,
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = if (running) Color.Black else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Two-pane browser over the tools a plugin publishes: search + list on the left, detail right. */
@Composable
private fun McpToolsPane(
    tools: ImmutableList<McpToolSummary>,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedToolName: String?,
    onSelectTool: (String) -> Unit,
    runningToolName: String?,
    callCounts: Map<String, Int>,
    modifier: Modifier,
) {
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
    val selected = filtered.firstOrNull { it.name == selectedToolName } ?: filtered.firstOrNull()

    Row(modifier = modifier) {
        // Left pane: search + tool list.
        Column(modifier = Modifier.width(300.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(Res.string.mcp_tools_search)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(8.dp))
            LazyColumn(modifier = Modifier.fillMaxHeight()) {
                items(filtered, key = { it.name }) { tool ->
                    val isSelected = tool.name == selected?.name
                    val isRunning = tool.name == runningToolName
                    val callCount = callCounts[tool.name] ?: 0
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            )
                            .clickable { onSelectTool(tool.name) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
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
                            // Takes the free space so the count and dot sit against the right edge.
                            modifier = Modifier.weight(1f),
                        )
                        if (callCount > 0 || isRunning) {
                            McpToolCallCountBadge(count = callCount, running = isRunning)
                        }
                    }
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
}

/** What an agent already did with this plugin, newest call first. */
@Composable
private fun McpCallHistoryPane(
    callHistory: ImmutableList<McpCallRecord>,
    modifier: Modifier,
) {
    if (callHistory.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.mcp_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    // Selection lives above the lazy list so it survives the row scrolling out of view.
    var selectedCallId by remember { mutableStateOf(callHistory.first().id) }
    val selected = callHistory.firstOrNull { it.id == selectedCallId } ?: callHistory.first()

    Row(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.width(300.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(callHistory, key = { it.id }) { record ->
                McpCallHistoryRow(
                    record = record,
                    selected = record.id == selected.id,
                    onSelect = { selectedCallId = record.id },
                )
            }
        }
        VerticalDivider(modifier = Modifier.padding(horizontal = 12.dp))
        McpCallDetailPane(
            record = selected,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

/** Right pane: everything recorded about the selected call, with copy actions. */
@Composable
private fun McpCallDetailPane(
    record: McpCallRecord,
    modifier: Modifier,
) {
    val statusLabel = stringResource(
        if (record.succeeded) Res.string.mcp_history_succeeded else Res.string.mcp_history_failed,
    )
    val finishedAt = formatCallTime(record.finishedAtEpochMillis)
    val renderedArguments = record.arguments.joinToString(separator = "\n") { "${it.name} = ${it.value}" }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = record.toolName.substringAfterLast('.'),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = record.toolName,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = if (record.succeeded) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = if (record.succeeded) AiOperatingAccentColor else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (record.succeeded) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Text(
                text = finishedAt,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.size(4.dp))
        Text(
            text = stringResource(Res.string.mcp_tools_parameters),
            style = MaterialTheme.typography.labelLarge,
        )
        if (record.arguments.isEmpty()) {
            Text(
                text = stringResource(Res.string.mcp_history_no_arguments),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            record.arguments.forEach { argument ->
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = argument.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = argument.value,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.size(4.dp))
        Text(
            text = stringResource(Res.string.mcp_history_response),
            style = MaterialTheme.typography.labelLarge,
        )
        if (record.response.isEmpty()) {
            Text(
                text = stringResource(Res.string.mcp_history_no_response),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Bounded and scrolled on its own so a long response stays readable instead of
                    // pushing the copy actions out of the pane.
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
            ) {
                Text(
                    text = record.response,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.size(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { clipboardManager.setText(AnnotatedString(record.toolName)) },
            ) {
                Text(stringResource(Res.string.mcp_history_copy_tool_name))
            }
            if (record.arguments.isNotEmpty()) {
                TextButton(
                    onClick = { clipboardManager.setText(AnnotatedString(renderedArguments)) },
                ) {
                    Text(stringResource(Res.string.mcp_history_copy_arguments))
                }
            }
            if (record.response.isNotEmpty()) {
                TextButton(
                    onClick = { clipboardManager.setText(AnnotatedString(record.response)) },
                ) {
                    Text(stringResource(Res.string.mcp_history_copy_response))
                }
            }
            TextButton(
                onClick = {
                    clipboardManager.setText(
                        AnnotatedString(
                            buildCallDetails(
                                toolName = record.toolName,
                                statusLabel = statusLabel,
                                finishedAt = finishedAt,
                                renderedArguments = renderedArguments,
                                response = record.response,
                            ),
                        ),
                    )
                },
            ) {
                Text(stringResource(Res.string.mcp_history_copy_details))
            }
        }
    }
}

@Composable
private fun McpCallHistoryRow(
    record: McpCallRecord,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val statusLabel = stringResource(
        if (record.succeeded) Res.string.mcp_history_succeeded else Res.string.mcp_history_failed,
    )
    val finishedAt = formatCallTime(record.finishedAtEpochMillis)
    val renderedArguments = record.arguments.joinToString(separator = "\n") { "${it.name} = ${it.value}" }

    val clipboardManager = LocalClipboardManager.current
    val copyToolNameLabel = stringResource(Res.string.mcp_history_copy_tool_name)
    val copyArgumentsLabel = stringResource(Res.string.mcp_history_copy_arguments)
    val copyResponseLabel = stringResource(Res.string.mcp_history_copy_response)
    val copyDetailsLabel = stringResource(Res.string.mcp_history_copy_details)

    ContextMenuArea(
        items = {
            buildList {
                add(
                    ContextMenuItem(copyToolNameLabel) {
                        clipboardManager.setText(AnnotatedString(record.toolName))
                    },
                )
                if (record.arguments.isNotEmpty()) {
                    add(
                        ContextMenuItem(copyArgumentsLabel) {
                            clipboardManager.setText(AnnotatedString(renderedArguments))
                        },
                    )
                }
                if (record.response.isNotEmpty()) {
                    add(
                        ContextMenuItem(copyResponseLabel) {
                            clipboardManager.setText(AnnotatedString(record.response))
                        },
                    )
                }
                add(
                    ContextMenuItem(copyDetailsLabel) {
                        clipboardManager.setText(
                            AnnotatedString(
                                buildCallDetails(
                                    toolName = record.toolName,
                                    statusLabel = statusLabel,
                                    finishedAt = finishedAt,
                                    renderedArguments = renderedArguments,
                                    response = record.response,
                                ),
                            ),
                        )
                    },
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .clickable(role = Role.Button, onClick = onSelect)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (record.succeeded) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = statusLabel,
                tint = if (record.succeeded) AiOperatingAccentColor else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = record.toolName.substringAfterLast('.'),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = finishedAt,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
private fun buildCallDetails(
    toolName: String,
    statusLabel: String,
    finishedAt: String,
    renderedArguments: String,
    response: String,
): String = buildString {
    appendLine(toolName)
    appendLine(statusLabel)
    append(finishedAt)
    if (renderedArguments.isNotEmpty()) {
        appendLine()
        append(renderedArguments)
    }
    if (response.isNotEmpty()) {
        // A blank line keeps the response apart from the arguments above it, which are otherwise
        // laid out the same way.
        appendLine()
        appendLine()
        append(response)
    }
}

/** Wall-clock time of day, which is what the user can line up against their own actions. */
private const val MCP_TOOLS_DIALOG_WINDOW_FRACTION = 0.8f

private val CallHistoryTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatCallTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(CallHistoryTimeFormatter)

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
