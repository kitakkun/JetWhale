package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.mcp_tools_no_match
import com.kitakkun.jetwhale.host.mcp_tools_parameters
import com.kitakkun.jetwhale.host.mcp_tools_required
import com.kitakkun.jetwhale.host.mcp_tools_search
import com.kitakkun.jetwhale.host.mcp_tools_search_clear
import com.kitakkun.jetwhale.host.model.McpToolParameterSummary
import com.kitakkun.jetwhale.host.model.McpToolSummary
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwSearchField
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwVerticalDivider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

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
            color = if (running) JwTheme.colors.onAiAccent else JwTheme.colors.onSurface,
        )
    }
}

/** Two-pane browser over the tools in scope: search + list on the left, detail right. */
@Composable
internal fun McpToolsPane(
    toolRows: ImmutableList<McpToolRowUiState>,
    query: String,
    selectedToolKey: String?,
    onQueryChange: (String) -> Unit,
    onSelectTool: (String) -> Unit,
    modifier: Modifier = Modifier,
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
private fun McpToolsPanePreview() {
    JwTheme(darkTheme = false) {
        McpToolsPane(
            toolRows = persistentListOf(
                McpToolRowUiState(
                    pluginId = "com.example.inspector",
                    pluginName = "Inspector",
                    tool = McpToolSummary(
                        name = "inspector.find",
                        description = "Finds the views whose text matches a query",
                        parameters = listOf(
                            McpToolParameterSummary(
                                name = "query",
                                type = "string",
                                description = "Text to look for",
                                required = true,
                            ),
                        ),
                    ),
                    callCount = 2,
                    running = true,
                ),
            ),
            query = "",
            selectedToolKey = null,
            onQueryChange = {},
            onSelectTool = {},
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
