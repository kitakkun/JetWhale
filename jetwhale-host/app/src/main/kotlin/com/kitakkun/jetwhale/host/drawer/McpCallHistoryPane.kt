package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
import com.kitakkun.jetwhale.host.mcp_tools_parameters
import com.kitakkun.jetwhale.host.model.McpCallArgument
import com.kitakkun.jetwhale.host.model.McpCallRecord
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwIconButtonDefaults
import com.kitakkun.jetwhale.host.ui.JwIcons
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwVerticalDivider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.awt.datatransfer.StringSelection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What agents already did in the selected scope, newest call first. */
@Composable
internal fun McpCallHistoryPane(
    callHistory: ImmutableList<McpCallRecord>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (callHistory.isEmpty()) {
            JwText(
                text = stringResource(Res.string.mcp_history_empty),
                style = JwTheme.textStyles.body,
                color = JwTheme.colors.textSecondary,
            )
        } else {
            // Selection lives above the lazy list so it survives the row scrolling out of view.
            var selectedCallId by remember { mutableStateOf(callHistory.first().id) }
            val selected = callHistory.firstOrNull { it.id == selectedCallId } ?: callHistory.first()

            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.width(320.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(callHistory, key = McpCallRecord::id) { record ->
                        McpCallHistoryRow(
                            record = record,
                            selected = record.id == selected.id,
                            onSelect = { selectedCallId = record.id },
                        )
                    }
                }
                JwVerticalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                McpCallDetailPane(
                    record = selected,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
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
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        McpCallSummary(
            toolName = record.toolName,
            succeeded = record.succeeded,
            statusLabel = statusLabel,
            finishedAt = finishedAt,
        )
        McpCallArguments(arguments = record.arguments, renderedArguments = renderedArguments)
        McpCallResponse(response = record.response)

        Spacer(Modifier.size(4.dp))
        JwButton(
            text = stringResource(Res.string.mcp_history_copy_details),
            style = JwButtonStyle.Text,
            onClick = {
                scope.launch {
                    clipboard.setPlainText(
                        buildCallDetails(
                            toolName = record.toolName,
                            statusLabel = statusLabel,
                            finishedAt = finishedAt,
                            renderedArguments = renderedArguments,
                            response = record.response,
                        ),
                    )
                }
            },
        )
    }
}

/** What the call was and how it ended: the tool's name, its outcome, and when it finished. */
@Composable
private fun ColumnScope.McpCallSummary(
    toolName: String,
    succeeded: Boolean,
    statusLabel: String,
    finishedAt: String,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        JwText(
            text = toolName.substringAfterLast('.'),
            style = JwTheme.textStyles.title,
            fontFamily = FontFamily.Monospace,
        )
        McpCopyIconButton(
            contentDescription = stringResource(Res.string.mcp_history_copy_tool_name),
            onClick = { scope.launch { clipboard.setPlainText(toolName) } },
        )
    }
    JwText(
        text = toolName,
        style = JwTheme.textStyles.code,
        color = JwTheme.colors.textSecondary,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        JwIcon(
            imageVector = if (succeeded) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (succeeded) JwTheme.colors.aiAccent else JwTheme.colors.error,
        )
        JwText(
            text = statusLabel,
            style = JwTheme.textStyles.label,
            color = if (succeeded) {
                JwTheme.colors.textSecondary
            } else {
                JwTheme.colors.error
            },
        )
        JwText(
            text = finishedAt,
            style = JwTheme.textStyles.code,
            color = JwTheme.colors.textSecondary,
        )
    }
}

/** The arguments the call was made with, name over value, or a note that it took none. */
@Composable
private fun ColumnScope.McpCallArguments(arguments: ImmutableList<McpCallArgument>, renderedArguments: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Spacer(Modifier.size(4.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        JwText(
            text = stringResource(Res.string.mcp_tools_parameters),
            style = JwTheme.textStyles.label,
        )
        if (arguments.isNotEmpty()) {
            McpCopyIconButton(
                contentDescription = stringResource(Res.string.mcp_history_copy_arguments),
                onClick = { scope.launch { clipboard.setPlainText(renderedArguments) } },
            )
        }
    }
    if (arguments.isEmpty()) {
        JwText(
            text = stringResource(Res.string.mcp_history_no_arguments),
            style = JwTheme.textStyles.bodySmall,
            color = JwTheme.colors.textSecondary,
        )
    } else {
        arguments.forEach { argument ->
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                JwText(
                    text = argument.name,
                    style = JwTheme.textStyles.code,
                )
                JwText(
                    text = argument.value,
                    style = JwTheme.textStyles.code,
                    color = JwTheme.colors.textSecondary,
                )
            }
        }
    }
}

/** What the call returned, or a note that it returned nothing. */
@Composable
private fun ColumnScope.McpCallResponse(response: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Spacer(Modifier.size(4.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        JwText(
            text = stringResource(Res.string.mcp_history_response),
            style = JwTheme.textStyles.label,
        )
        if (response.isNotEmpty()) {
            McpCopyIconButton(
                contentDescription = stringResource(Res.string.mcp_history_copy_response),
                onClick = { scope.launch { clipboard.setPlainText(response) } },
            )
        }
    }
    if (response.isEmpty()) {
        JwText(
            text = stringResource(Res.string.mcp_history_no_response),
            style = JwTheme.textStyles.bodySmall,
            color = JwTheme.colors.textSecondary,
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Bounded and scrolled on its own so a long response stays readable instead of
                // pushing the copy action out of the pane.
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(JwTheme.colors.neutralContainer)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
        ) {
            JwText(
                text = response,
                style = JwTheme.textStyles.code,
                color = JwTheme.colors.textSecondary,
            )
        }
    }
}

/** Copy affordance that sits beside a heading without competing with it for attention. */
@Composable
private fun McpCopyIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwIconButton(
        modifier = modifier,
        onClick = onClick,
        tooltip = contentDescription,
        size = JwIconButtonDefaults.inlineSize,
    ) {
        JwIcon(
            imageVector = JwIcons.Copy,
            contentDescription = null,
            tint = JwTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun McpCallHistoryRow(
    record: McpCallRecord,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusLabel = stringResource(
        if (record.succeeded) Res.string.mcp_history_succeeded else Res.string.mcp_history_failed,
    )
    val finishedAt = formatCallTime(record.finishedAtEpochMillis)
    val renderedArguments = record.arguments.joinToString(separator = "\n") { "${it.name} = ${it.value}" }

    val scope = rememberCoroutineScope()
    val copyToolNameLabel = stringResource(Res.string.mcp_history_copy_tool_name)
    val copyArgumentsLabel = stringResource(Res.string.mcp_history_copy_arguments)
    val copyResponseLabel = stringResource(Res.string.mcp_history_copy_response)
    val copyDetailsLabel = stringResource(Res.string.mcp_history_copy_details)

    val clipboard = LocalClipboard.current
    ContextMenuArea(
        items = {
            buildList {
                add(
                    ContextMenuItem(copyToolNameLabel) {
                        scope.launch { clipboard.setPlainText(record.toolName) }
                    },
                )
                if (record.arguments.isNotEmpty()) {
                    add(
                        ContextMenuItem(copyArgumentsLabel) {
                            scope.launch { clipboard.setPlainText(renderedArguments) }
                        },
                    )
                }
                if (record.response.isNotEmpty()) {
                    add(
                        ContextMenuItem(copyResponseLabel) {
                            scope.launch { clipboard.setPlainText(record.response) }
                        },
                    )
                }
                add(
                    ContextMenuItem(copyDetailsLabel) {
                        scope.launch {
                            clipboard.setPlainText(
                                buildCallDetails(
                                    toolName = record.toolName,
                                    statusLabel = statusLabel,
                                    finishedAt = finishedAt,
                                    renderedArguments = renderedArguments,
                                    response = record.response,
                                ),
                            )
                        }
                    },
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) JwTheme.colors.selection else Color.Transparent)
                .clickable(role = Role.Button, onClick = onSelect)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            JwIcon(
                imageVector = if (record.succeeded) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = statusLabel,
                tint = if (record.succeeded) JwTheme.colors.aiAccent else JwTheme.colors.error,
            )
            JwText(
                text = record.toolName.substringAfterLast('.'),
                style = JwTheme.textStyles.code,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) JwTheme.colors.onSelection else JwTheme.colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            JwText(
                text = finishedAt,
                style = JwTheme.textStyles.code,
                color = JwTheme.colors.textSecondary,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private suspend fun Clipboard.setPlainText(text: String) {
    setClipEntry(ClipEntry(StringSelection(text)))
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

@Preview
@Composable
private fun McpCallHistoryPanePreview() {
    JwTheme(darkTheme = false) {
        McpCallHistoryPane(
            callHistory = persistentListOf(
                McpCallRecord(
                    id = 2,
                    toolName = "inspector.dump",
                    pluginId = "com.example.inspector",
                    sessionId = "session-1",
                    succeeded = true,
                    finishedAtEpochMillis = 0,
                    arguments = persistentListOf(McpCallArgument(name = "depth", value = "2")),
                    response = "{}",
                ),
                McpCallRecord(
                    id = 1,
                    toolName = "inspector.select",
                    pluginId = "com.example.inspector",
                    sessionId = "session-1",
                    succeeded = false,
                    finishedAtEpochMillis = 0,
                    arguments = persistentListOf(),
                    response = "",
                ),
            ),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
