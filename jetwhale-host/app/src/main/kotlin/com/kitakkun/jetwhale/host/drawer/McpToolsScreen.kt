package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import com.kitakkun.jetwhale.host.Res
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
import com.kitakkun.jetwhale.host.mcp_tools_filter_add
import com.kitakkun.jetwhale.host.mcp_tools_filter_all
import com.kitakkun.jetwhale.host.mcp_tools_filter_plugin
import com.kitakkun.jetwhale.host.mcp_tools_filter_remove
import com.kitakkun.jetwhale.host.mcp_tools_filter_session
import com.kitakkun.jetwhale.host.mcp_tools_no_match
import com.kitakkun.jetwhale.host.mcp_tools_parameters
import com.kitakkun.jetwhale.host.mcp_tools_required
import com.kitakkun.jetwhale.host.mcp_tools_search
import com.kitakkun.jetwhale.host.mcp_tools_tab_history
import com.kitakkun.jetwhale.host.mcp_tools_tab_tools
import com.kitakkun.jetwhale.host.model.McpCallRecord
import com.kitakkun.jetwhale.host.model.McpToolParameterSummary
import com.kitakkun.jetwhale.host.ui.JwSearchField
import com.kitakkun.jetwhale.host.ui.JwTab
import com.kitakkun.jetwhale.host.ui.JwTabRow
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Everything the MCP tools browser draws, already narrowed to the selected plugin and session. */
data class McpToolsScreenUiState(
    val pluginOptions: ImmutableList<McpFilterOption>,
    val sessionOptions: ImmutableList<McpFilterOption>,
    val selectedPluginIds: ImmutableSet<String>,
    val selectedSessionIds: ImmutableSet<String>,
    val toolRows: ImmutableList<McpToolRowUiState>,
    val callHistory: ImmutableList<McpCallRecord>,
    val runningToolName: String?,
)

/** The panes the MCP browser can show: the tools plugins publish, or the calls already made. */
internal enum class McpToolsTab {
    Tools,
    History,
}

private const val MCP_TOOLS_DIALOG_WINDOW_FRACTION = 0.8f

@Composable
fun McpToolsScreen(
    uiState: McpToolsScreenUiState,
    onSelectPluginFilters: (Set<String>) -> Unit,
    onSelectSessionFilters: (Set<String>) -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier
                // Take most of the window so tool descriptions and history are readable, but stop
                // growing past a comfortable reading width on a large display.
                .fillMaxSize(MCP_TOOLS_DIALOG_WINDOW_FRACTION)
                .sizeIn(
                    minWidth = 640.dp,
                    minHeight = 440.dp,
                    maxWidth = 1200.dp,
                    maxHeight = 860.dp,
                )
                .padding(20.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    McpFilterChipGroup(
                        label = stringResource(Res.string.mcp_tools_filter_plugin),
                        options = uiState.pluginOptions,
                        selectedIds = uiState.selectedPluginIds,
                        onSelectionChange = onSelectPluginFilters,
                    )
                    McpFilterChipGroup(
                        label = stringResource(Res.string.mcp_tools_filter_session),
                        options = uiState.sessionOptions,
                        selectedIds = uiState.selectedSessionIds,
                        onSelectionChange = onSelectSessionFilters,
                    )
                }
                Text(
                    text = stringResource(
                        if (uiState.runningToolName != null) Res.string.mcp_tool_executing else Res.string.mcp_tools_available,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Held against the first chip row instead of the middle of a block whose height
                    // grows as chips wrap.
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.size(12.dp))

            var selectedTab by remember { mutableStateOf(McpToolsTab.Tools) }
            JwTabRow {
                JwTab(
                    text = stringResource(Res.string.mcp_tools_tab_tools),
                    selected = selectedTab == McpToolsTab.Tools,
                    onClick = { selectedTab = McpToolsTab.Tools },
                )
                // How many calls the current scope holds, so the count is visible without opening
                // the tab.
                JwTab(
                    text = stringResource(Res.string.mcp_tools_tab_history),
                    count = uiState.callHistory.size.takeIf { it > 0 },
                    selected = selectedTab == McpToolsTab.History,
                    onClick = { selectedTab = McpToolsTab.History },
                )
            }
            Spacer(Modifier.size(12.dp))

            // Hoisted out of the pane so switching tabs and coming back keeps the search and the
            // selected tool where the user left them.
            var query by remember { mutableStateOf("") }
            var selectedToolKey by remember { mutableStateOf<String?>(null) }

            when (selectedTab) {
                McpToolsTab.Tools -> McpToolsPane(
                    toolRows = uiState.toolRows,
                    query = query,
                    onQueryChange = { query = it },
                    selectedToolKey = selectedToolKey,
                    onSelectTool = { selectedToolKey = it },
                    modifier = Modifier.weight(1f),
                )

                McpToolsTab.History -> McpCallHistoryPane(
                    callHistory = uiState.callHistory,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Icon size shared by the filter chips and the entries of their picker. */
private val McpFilterIconSize = 18.dp

/**
 * One filter group: a removable chip per picked value, plus a chip that opens the picker. An empty
 * [selectedIds] narrows nothing, and the group reads as "All" until a value is picked.
 */
@Composable
private fun McpFilterChipGroup(
    label: String,
    options: ImmutableList<McpFilterOption>,
    selectedIds: ImmutableSet<String>,
    onSelectionChange: (Set<String>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val allLabel = stringResource(Res.string.mcp_tools_filter_all)
    val addLabel = stringResource(Res.string.mcp_tools_filter_add)
    val removeLabel = stringResource(Res.string.mcp_tools_filter_remove)
    // A picked value outlives the option that named it once a session goes away, so the raw id
    // stands in rather than dropping a filter that is still narrowing the screen.
    val selectedChips = remember(options, selectedIds) {
        val labelsById = options.associate { it.id to it.label }
        selectedIds
            .map { id -> McpFilterOption(id = id, label = labelsById[id] ?: id) }
            .sortedBy { it.label }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Fixed width and held against the first chip row so both groups' labels line up even
            // when one of them wraps onto several rows.
            modifier = Modifier
                .width(76.dp)
                .padding(top = 8.dp),
        )
        // Wraps so a long list of plugins or sessions grows downwards instead of widening a dialog
        // that is already bounded.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            selectedChips.forEach { option ->
                InputChip(
                    selected = true,
                    onClick = { onSelectionChange(selectedIds - option.id) },
                    label = {
                        Text(
                            text = option.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 220.dp),
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = removeLabel,
                            modifier = Modifier.size(McpFilterIconSize),
                        )
                    },
                )
            }
            Box {
                val filtering = selectedChips.isNotEmpty()
                val addIcon: (@Composable () -> Unit)? = if (filtering) {
                    {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(McpFilterIconSize),
                        )
                    }
                } else {
                    null
                }
                AssistChip(
                    onClick = { expanded = true },
                    enabled = options.isNotEmpty(),
                    label = { Text(if (filtering) addLabel else allLabel) },
                    leadingIcon = addIcon,
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    // The menu survives every pick so several values can be added in one go, and a
                    // check marks what is already picked while it is open.
                    DropdownMenuItem(
                        text = { Text(allLabel) },
                        onClick = { onSelectionChange(emptySet()) },
                        trailingIcon = { if (selectedIds.isEmpty()) McpFilterSelectedCheck() },
                    )
                    options.forEach { option ->
                        val selected = option.id in selectedIds
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onSelectionChange(
                                    if (selected) selectedIds - option.id else selectedIds + option.id,
                                )
                            },
                            trailingIcon = { if (selected) McpFilterSelectedCheck() },
                        )
                    }
                }
            }
        }
    }
}

/** Marks an entry of a filter picker as already narrowing the screen. */
@Composable
private fun McpFilterSelectedCheck() {
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        modifier = Modifier.size(McpFilterIconSize),
    )
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
                if (running) JwTheme.colors.aiAccent else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape,
            )
            .then(
                if (running) Modifier.aiOperatingBorder(color = JwTheme.colors.aiAccent, width = 2.dp, cornerRadius = 4.dp) else Modifier,
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

/** Two-pane browser over the tools in scope: search + list on the left, detail right. */
@Composable
private fun McpToolsPane(
    toolRows: ImmutableList<McpToolRowUiState>,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedToolKey: String?,
    onSelectTool: (String) -> Unit,
    modifier: Modifier,
) {
    val filtered = remember(query, toolRows) {
        if (query.isBlank()) {
            toolRows
        } else {
            toolRows.filter {
                it.tool.name.contains(query, ignoreCase = true) ||
                    it.tool.description.contains(query, ignoreCase = true) ||
                    it.pluginName.contains(query, ignoreCase = true)
            }
        }
    }
    val selected = filtered.firstOrNull { it.key == selectedToolKey } ?: filtered.firstOrNull()

    Row(modifier = modifier) {
        // Left pane: search + tool list.
        Column(modifier = Modifier.width(320.dp)) {
            JwSearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(Res.string.mcp_tools_search),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(8.dp))
            LazyColumn(modifier = Modifier.fillMaxHeight()) {
                items(filtered, key = { it.key }) { row ->
                    val isSelected = row.key == selected?.key
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) JwTheme.colors.selection else Color.Transparent,
                            )
                            .clickable { onSelectTool(row.key) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        // Takes the free space so the count sits against the right edge.
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.tool.name.substringAfterLast('.'),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            // The short name alone is ambiguous once plugins are mixed, so every row
                            // names the plugin that publishes the tool.
                            Text(
                                text = row.pluginName,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (row.callCount > 0 || row.running) {
                            McpToolCallCountBadge(count = row.callCount, running = row.running)
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
                    text = selected.tool.name.substringAfterLast('.'),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = selected.pluginName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = selected.tool.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = selected.tool.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (selected.tool.parameters.isNotEmpty()) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = stringResource(Res.string.mcp_tools_parameters),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    selected.tool.parameters.forEach { param -> McpParameterRow(param) }
                }
            }
        }
    }
}

/** What agents already did in the selected scope, newest call first. */
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
            modifier = Modifier.width(320.dp),
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

/**
 * Right pane: everything recorded about the selected call. Each section carries its own inline copy
 * icon; the one action that takes the whole record stays a labelled button.
 */
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = record.toolName.substringAfterLast('.'),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
            )
            McpCopyIconButton(
                contentDescription = stringResource(Res.string.mcp_history_copy_tool_name),
                onClick = { clipboardManager.setText(AnnotatedString(record.toolName)) },
            )
        }
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
                tint = if (record.succeeded) JwTheme.colors.aiAccent else MaterialTheme.colorScheme.error,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.mcp_tools_parameters),
                style = MaterialTheme.typography.labelLarge,
            )
            if (record.arguments.isNotEmpty()) {
                McpCopyIconButton(
                    contentDescription = stringResource(Res.string.mcp_history_copy_arguments),
                    onClick = { clipboardManager.setText(AnnotatedString(renderedArguments)) },
                )
            }
        }
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.mcp_history_response),
                style = MaterialTheme.typography.labelLarge,
            )
            if (record.response.isNotEmpty()) {
                McpCopyIconButton(
                    contentDescription = stringResource(Res.string.mcp_history_copy_response),
                    onClick = { clipboardManager.setText(AnnotatedString(record.response)) },
                )
            }
        }
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
                    // pushing the copy action out of the pane.
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

/** Copy affordance that sits beside a heading without competing with it for attention. */
@Composable
private fun McpCopyIconButton(
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(24.dp),
    ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
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
                tint = if (record.succeeded) JwTheme.colors.aiAccent else MaterialTheme.colorScheme.error,
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
