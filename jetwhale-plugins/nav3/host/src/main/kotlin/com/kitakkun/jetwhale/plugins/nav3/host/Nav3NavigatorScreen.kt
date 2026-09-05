package com.kitakkun.jetwhale.plugins.nav3.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwFormField
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwPanel
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTab
import com.kitakkun.jetwhale.host.ui.JwTabRow
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTagStyle
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwToolbar
import com.kitakkun.jetwhale.host.ui.JwTypography
import com.kitakkun.jetwhale.host.ui.JwVerticalDivider
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackOperation
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackSnapshot
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeySnapshot
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyTypeDescriptor
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A one-line outcome of the last thing the user asked for. */
internal data class Nav3Status(val message: String, val isError: Boolean)

private val PrettyJson = Json { prettyPrint = true }

/** Room for a small key's JSON without scrolling, so the editor reads as an editor when empty. */
private val EditorMinHeight = 120.dp

@Composable
internal fun Nav3NavigatorScreen(
    stacks: List<NavBackStackSnapshot>,
    keyTypes: List<NavKeyTypeDescriptor>,
    selectedStackId: String?,
    status: Nav3Status?,
    onSelectStack: (String) -> Unit,
    onApplyOperation: (stackId: String, operation: NavBackStackOperation) -> Unit,
    onRefresh: () -> Unit,
) {
    val selected = stacks.firstOrNull { it.stackId == selectedStackId } ?: stacks.firstOrNull()
    // The draft survives plugin reloads and host restarts, so a half-written key is not lost to a
    // hot reload in the middle of composing one.
    var draft by rememberPersistent("push-draft", default = "")

    Column(Modifier.fillMaxSize()) {
        JwToolbar(
            title = "Navigation 3",
            actions = {
                JwButton(text = "Reload from app", onClick = onRefresh, style = JwButtonStyle.Text)
            },
        )
        if (stacks.size > 1) {
            JwTabRow {
                stacks.forEach { stack ->
                    JwTab(
                        text = stack.stackId,
                        selected = stack.stackId == selected?.stackId,
                        onClick = { onSelectStack(stack.stackId) },
                    )
                }
            }
        }

        status?.let { StatusBanner(it) }

        if (selected == null) {
            JwEmptyState(
                title = "No back stack registered",
                description = "The app has not registered a Navigation 3 back stack yet. Add TrackNavBackStack(backStack) next to the NavDisplay that renders it, then reload.",
            )
        } else {
            Row(Modifier.fillMaxSize()) {
                BackStackPane(
                    snapshot = selected,
                    onApplyOperation = { onApplyOperation(selected.stackId, it) },
                    onCopyKeyToEditor = { draft = PrettyJson.encodeToString(JsonElement.serializer(), it) },
                    modifier = Modifier.weight(0.55f),
                )
                JwVerticalDivider()
                PushPane(
                    keyTypes = keyTypes,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onApplyOperation = { onApplyOperation(selected.stackId, it) },
                    modifier = Modifier.weight(0.45f),
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(status: Nav3Status) {
    JwBanner(
        text = status.message,
        tone = if (status.isError) JwTone.Error else JwTone.Neutral,
    )
}

@Composable
private fun BackStackPane(
    snapshot: NavBackStackSnapshot,
    onApplyOperation: (NavBackStackOperation) -> Unit,
    onCopyKeyToEditor: (JsonElement) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = JwSpacing.large, vertical = JwSpacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Back stack · ${snapshot.entries.size} ${if (snapshot.entries.size == 1) "entry" else "entries"}",
                style = MaterialTheme.typography.titleSmall,
            )
            JwButton(
                text = "Pop",
                onClick = { onApplyOperation(NavBackStackOperation.Pop(count = 1)) },
                enabled = snapshot.entries.size > 1,
            )
        }
        JwHorizontalDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(JwSpacing.medium),
            contentPadding = PaddingValues(JwSpacing.large),
        ) {
            itemsIndexed(snapshot.entries) { index, entry ->
                BackStackEntryCard(
                    index = index,
                    entry = entry,
                    isCurrent = index == snapshot.entries.lastIndex,
                    canRemove = snapshot.entries.size > 1,
                    onApplyOperation = onApplyOperation,
                    onCopyKeyToEditor = onCopyKeyToEditor,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackStackEntryCard(
    index: Int,
    entry: NavKeySnapshot,
    isCurrent: Boolean,
    canRemove: Boolean,
    onApplyOperation: (NavBackStackOperation) -> Unit,
    onCopyKeyToEditor: (JsonElement) -> Unit,
) {
    JwPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
            Text("#$index", style = MaterialTheme.typography.labelMedium, color = JwTheme.colors.textSecondary)
            Text(entry.typeName, style = MaterialTheme.typography.titleSmall)
            if (isCurrent) {
                JwTag(text = "current", tone = JwTone.Accent, style = JwTagStyle.Tinted)
            }
        }
        Text(
            text = entry.display,
            style = JwTypography.code,
        )
        // The actions have to wrap: a plugin pane can be narrow, and a row that overflows
        // squeezes the last label into one character per line instead of moving it down.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall)) {
            if (!isCurrent) {
                JwButton(
                    text = "Pop to here",
                    onClick = { onApplyOperation(NavBackStackOperation.PopTo(index = index, inclusive = false)) },
                    style = JwButtonStyle.Text,
                )
                JwButton(
                    text = "To top",
                    onClick = { onApplyOperation(NavBackStackOperation.MoveToTop(index = index)) },
                    style = JwButtonStyle.Text,
                )
            }
            JwButton(
                text = "Remove",
                onClick = { onApplyOperation(NavBackStackOperation.RemoveAt(index = index)) },
                enabled = canRemove,
                style = JwButtonStyle.Text,
            )
            entry.key?.let { key ->
                JwButton(text = "Copy to editor", onClick = { onCopyKeyToEditor(key) }, style = JwButtonStyle.Text)
            }
        }
    }
}

@Composable
private fun PushPane(
    keyTypes: List<NavKeyTypeDescriptor>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onApplyOperation: (NavBackStackOperation) -> Unit,
    modifier: Modifier,
) {
    var editorError by remember { mutableStateOf<String?>(null) }

    fun withParsedKey(action: (JsonObject) -> Unit) {
        when (val key = parseNavKey(draft)) {
            null -> editorError = "Not a JSON object. A key looks like {\"type\":\"Detail\",\"id\":\"42\"}."

            else -> {
                editorError = null
                action(key)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(JwSpacing.large),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.large),
    ) {
        JwFormField(
            label = "Push a NavKey",
            supportingText = editorError,
            isError = editorError != null,
        ) {
            JwTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = "{\"type\": \"…\"}",
                singleLine = false,
                textStyle = JwTypography.code,
                modifier = Modifier.fillMaxWidth().heightIn(min = EditorMinHeight),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
            JwButton(
                text = "Push",
                onClick = { withParsedKey { onApplyOperation(NavBackStackOperation.Push(key = it, index = null)) } },
                enabled = draft.isNotBlank(),
                style = JwButtonStyle.Primary,
            )
            JwButton(
                text = "Replace stack",
                onClick = { withParsedKey { onApplyOperation(NavBackStackOperation.ReplaceAll(keys = listOf(it))) } },
                enabled = draft.isNotBlank(),
            )
        }

        JwHorizontalDivider()

        if (keyTypes.isEmpty()) {
            Text(
                "The app exposed no constructible key types. You can still copy an existing entry's key with \"Copy to editor\" and edit it.",
                style = MaterialTheme.typography.bodySmall,
                color = JwTheme.colors.textSecondary,
            )
        } else {
            JwSectionHeader(title = "Key types · click one to fill the editor", contentPadding = PaddingValues(0.dp))
            keyTypes.forEach { type ->
                KeyTypeRow(
                    type = type,
                    onClick = {
                        onDraftChange(PrettyJson.encodeToString(JsonElement.serializer(), type.template))
                        editorError = null
                    },
                )
            }
        }
    }
}

@Composable
private fun KeyTypeRow(type: NavKeyTypeDescriptor, onClick: () -> Unit) {
    val fields = type.fields.joinToString { field ->
        buildString {
            append(field.name)
            append(": ")
            append(field.type)
            if (field.optional) append(" = …")
        }
    }
    JwListItem(
        text = type.serialName,
        selected = false,
        onClick = onClick,
        trailingContent = {
            if (fields.isNotEmpty()) {
                Text(
                    text = fields,
                    style = JwTypography.code,
                    color = JwTheme.colors.textSecondary,
                    maxLines = 1,
                )
            }
        },
    )
}

private fun parseNavKey(text: String): JsonObject? = try {
    Json.parseToJsonElement(text) as? JsonObject
} catch (_: SerializationException) {
    null
}
