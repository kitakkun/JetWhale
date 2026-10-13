package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.kitakkun.jetwhale.host.mcp_tools_tab_history
import com.kitakkun.jetwhale.host.mcp_tools_tab_tools
import com.kitakkun.jetwhale.host.model.McpCallRecord
import com.kitakkun.jetwhale.host.model.McpToolSummary
import com.kitakkun.jetwhale.host.ui.JwDropdownButton
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIcons
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwShapes
import com.kitakkun.jetwhale.host.ui.JwSurface
import com.kitakkun.jetwhale.host.ui.JwTab
import com.kitakkun.jetwhale.host.ui.JwTabRow
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTagStyle
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
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
enum class McpToolsTab {
    Tools,
    History,
}

private const val MCP_TOOLS_DIALOG_WINDOW_FRACTION = 0.8f

@Composable
fun McpToolsScreen(
    uiState: McpToolsScreenUiState,
    initialTab: McpToolsTab,
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
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.size(12.dp))

            var selectedTab by remember { mutableStateOf(initialTab) }
            JwTabRow {
                JwTab(
                    text = stringResource(Res.string.mcp_tools_tab_tools),
                    selected = selectedTab == McpToolsTab.Tools,
                    onClick = { selectedTab = McpToolsTab.Tools },
                )
                JwTab(
                    text = stringResource(Res.string.mcp_tools_tab_history),
                    count = uiState.callHistory.size.takeIf { it > 0 },
                    selected = selectedTab == McpToolsTab.History,
                    onClick = { selectedTab = McpToolsTab.History },
                )
            }
            Spacer(Modifier.size(12.dp))

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
            modifier = Modifier
                .width(76.dp)
                .padding(top = 8.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            selectedChips.forEach { option ->
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
            initialTab = McpToolsTab.Tools,
            onSelectPluginFilters = {},
            onSelectSessionFilters = {},
        )
    }
}
