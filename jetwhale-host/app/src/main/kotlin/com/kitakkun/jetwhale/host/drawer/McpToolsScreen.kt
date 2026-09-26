package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
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
import com.kitakkun.jetwhale.host.mcp_tools_search_clear
import com.kitakkun.jetwhale.host.mcp_tools_tab_history
import com.kitakkun.jetwhale.host.mcp_tools_tab_tools
import com.kitakkun.jetwhale.host.model.McpCallRecord
import com.kitakkun.jetwhale.host.model.McpToolParameterSummary
import com.kitakkun.jetwhale.host.model.McpToolSummary
import com.kitakkun.jetwhale.host.ui.JwDropdownButton
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIcons
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwSearchField
import com.kitakkun.jetwhale.host.ui.JwShapes
import com.kitakkun.jetwhale.host.ui.JwSurface
import com.kitakkun.jetwhale.host.ui.JwTab
import com.kitakkun.jetwhale.host.ui.JwTabRow
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTagStyle
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwVerticalDivider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.jetbrains.compose.resources.stringResource

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
    modifier: Modifier = Modifier,
) {
    JwSurface(modifier = modifier, color = JwTheme.colors.elevatedBackground, shape = JwShapes.large) {
        Column(
            modifier = Modifier
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
                JwText(
                    text = stringResource(
                        if (uiState.runningToolName != null) Res.string.mcp_tool_executing else Res.string.mcp_tools_available,
                    ),
                    style = JwTheme.textStyles.label,
                    color = JwTheme.colors.textSecondary,
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

/** The widest a filter tag grows before its label is cut, so one long session name cannot own the row. */
private val McpFilterChipMaxWidth = 220.dp

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
    modifier: Modifier = Modifier,
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
            .sortedBy(McpFilterOption::label)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        JwText(
            text = label,
            style = JwTheme.textStyles.label,
            color = JwTheme.colors.textSecondary,
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
                // Clicking the tag removes the value; the close glyph names what the click does.
                JwTag(
                    text = option.label,
                    tone = JwTone.Accent,
                    style = JwTagStyle.Tinted,
                    onClick = { onSelectionChange(selectedIds - option.id) },
                    trailingIcon = { JwIcon(imageVector = JwIcons.Close, contentDescription = removeLabel) },
                    modifier = Modifier.widthIn(max = McpFilterChipMaxWidth),
                )
            }
            val filtering = selectedChips.isNotEmpty()
            // The menu survives every pick so several values can be added in one go, and a check
            // marks what is already picked while it is open.
            JwDropdownButton(
                text = if (filtering) addLabel else allLabel,
                expanded = expanded,
                onExpandedChange = { expanded = it },
                enabled = options.isNotEmpty(),
                leadingIcon = if (filtering) {
                    { JwIcon(imageVector = Icons.Default.Add, contentDescription = null) }
                } else {
                    null
                },
                modifier = Modifier.width(IntrinsicSize.Max),
            ) {
                JwMenuItem(
                    text = allLabel,
                    selected = selectedIds.isEmpty(),
                    onClick = { onSelectionChange(emptySet()) },
                )
                options.forEach { option ->
                    val selected = option.id in selectedIds
                    JwMenuItem(
                        text = option.label,
                        selected = selected,
                        onClick = {
                            onSelectionChange(
                                if (selected) selectedIds - option.id else selectedIds + option.id,
                            )
                        },
                    )
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
                if (running) JwTheme.colors.aiAccent else JwTheme.colors.neutralContainer,
                shape,
            )
            .then(
                if (running) Modifier.aiOperatingBorder(color = JwTheme.colors.aiAccent, width = 2.dp, cornerRadius = 4.dp) else Modifier,
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        JwText(
            text = count.toString(),
            style = JwTheme.textStyles.code,
            color = if (running) Color.Black else JwTheme.colors.onSurface,
        )
    }
}

/** Two-pane browser over the tools in scope: search + list on the left, detail right. */
@Composable
private fun McpToolsPane(
    toolRows: ImmutableList<McpToolRowUiState>,
    query: String,
    selectedToolKey: String?,
    modifier: Modifier,
    onQueryChange: (String) -> Unit,
    onSelectTool: (String) -> Unit,
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
        Column(modifier = Modifier.width(320.dp)) {
            JwSearchField(
                value = query,
                onValueChange = onQueryChange,
                clearLabel = stringResource(Res.string.mcp_tools_search_clear),
                placeholder = stringResource(Res.string.mcp_tools_search),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(8.dp))
            LazyColumn(modifier = Modifier.fillMaxHeight()) {
                items(filtered, key = McpToolRowUiState::key) { row ->
                    val isSelected = row.key == selected?.key
                    JwListItem(selected = isSelected, onClick = { onSelectTool(row.key) }) {
                        Column(modifier = Modifier.weight(1f)) {
                            JwText(
                                text = row.tool.name.substringAfterLast('.'),
                                style = JwTheme.textStyles.code,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = JwTheme.colors.onSurface,
                            )
                            // The short name alone is ambiguous once plugins are mixed, so every row
                            // names the plugin that publishes the tool.
                            JwText(
                                text = row.pluginName,
                                style = JwTheme.textStyles.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = JwTheme.colors.textSecondary,
                            )
                        }
                        if (row.callCount > 0 || row.running) {
                            McpToolCallCountBadge(count = row.callCount, running = row.running)
                        }
                    }
                }
            }
        }
        JwVerticalDivider(modifier = Modifier.padding(horizontal = 12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selected == null) {
                JwText(
                    text = stringResource(Res.string.mcp_tools_no_match),
                    color = JwTheme.colors.textSecondary,
                )
            } else {
                JwText(
                    text = selected.tool.name.substringAfterLast('.'),
                    style = JwTheme.textStyles.title,
                    fontFamily = FontFamily.Monospace,
                )
                JwText(
                    text = selected.pluginName,
                    style = JwTheme.textStyles.label,
                    color = JwTheme.colors.textSecondary,
                )
                JwText(
                    text = selected.tool.name,
                    style = JwTheme.textStyles.code,
                    color = JwTheme.colors.textSecondary,
                )
                JwText(
                    text = selected.tool.description,
                    style = JwTheme.textStyles.body,
                )
                if (selected.tool.parameters.isNotEmpty()) {
                    Spacer(Modifier.size(4.dp))
                    JwText(
                        text = stringResource(Res.string.mcp_tools_parameters),
                        style = JwTheme.textStyles.label,
                    )
                    selected.tool.parameters.forEach { param -> McpParameterRow(param) }
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
            JwText(
                text = param.name,
                style = JwTheme.textStyles.code,
            )
            if (param.type.isNotEmpty()) {
                JwText(
                    text = param.type,
                    style = JwTheme.textStyles.code,
                    color = JwTheme.colors.textSecondary,
                )
            }
            if (param.required) {
                JwText(
                    text = stringResource(Res.string.mcp_tools_required),
                    style = JwTheme.textStyles.labelSmall,
                    color = JwTheme.colors.error,
                )
            }
        }
        if (param.description.isNotEmpty()) {
            JwText(
                text = param.description,
                style = JwTheme.textStyles.bodySmall,
                color = JwTheme.colors.textSecondary,
            )
        }
    }
}

@Preview
@Composable
private fun McpToolsScreenPreview() {
    JwTheme(darkTheme = false) {
        McpToolsScreen(
            uiState = McpToolsScreenUiState(
                pluginOptions = persistentListOf(McpFilterOption(id = "com.example.inspector", label = "Inspector")),
                sessionOptions = persistentListOf(McpFilterOption(id = "session-1", label = "Sample app")),
                selectedPluginIds = persistentSetOf(),
                selectedSessionIds = persistentSetOf(),
                toolRows = persistentListOf(
                    McpToolRowUiState(
                        pluginId = "com.example.inspector",
                        pluginName = "Inspector",
                        tool = McpToolSummary(
                            name = "inspector.dump",
                            description = "Dumps the current view tree",
                            parameters = emptyList(),
                        ),
                        callCount = 3,
                        running = false,
                    ),
                ),
                callHistory = persistentListOf(
                    McpCallRecord(
                        id = 1,
                        toolName = "inspector.dump",
                        pluginId = "com.example.inspector",
                        sessionId = "session-1",
                        succeeded = true,
                        finishedAtEpochMillis = 0,
                        arguments = persistentListOf(),
                        response = "{}",
                    ),
                ),
                runningToolName = null,
            ),
            onSelectPluginFilters = {},
            onSelectSessionFilters = {},
        )
    }
}

@Preview
@Composable
private fun McpToolCallCountBadgePreview() {
    JwTheme(darkTheme = false) {
        McpToolCallCountBadge(count = 3, running = true)
    }
}
