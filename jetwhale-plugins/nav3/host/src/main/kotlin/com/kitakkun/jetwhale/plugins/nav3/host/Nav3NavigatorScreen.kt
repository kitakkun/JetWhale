package com.kitakkun.jetwhale.plugins.nav3.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Navigation 3") },
                actions = {
                    TextButton(onClick = onRefresh) { Text("Reload from app") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (stacks.size > 1) {
                SecondaryScrollableTabRow(
                    selectedTabIndex = stacks.indexOfFirst { it.stackId == selected?.stackId }.coerceAtLeast(0),
                ) {
                    stacks.forEach { stack ->
                        Tab(
                            selected = stack.stackId == selected?.stackId,
                            onClick = { onSelectStack(stack.stackId) },
                            text = { Text(stack.stackId) },
                        )
                    }
                }
            }

            status?.let { StatusBanner(it) }

            if (selected == null) {
                EmptyState()
            } else {
                Row(Modifier.fillMaxSize()) {
                    BackStackPane(
                        snapshot = selected,
                        onApplyOperation = { onApplyOperation(selected.stackId, it) },
                        onCopyKeyToEditor = { draft = PrettyJson.encodeToString(JsonElement.serializer(), it) },
                        modifier = Modifier.weight(0.55f),
                    )
                    VerticalDivider()
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
}

@Composable
private fun StatusBanner(status: Nav3Status) {
    Surface(
        color = if (status.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = status.message,
            style = MaterialTheme.typography.bodySmall,
            color = if (status.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("No back stack registered", style = MaterialTheme.typography.titleMedium)
        Text(
            "The app has not registered a Navigation 3 back stack yet. Add TrackNavBackStack(backStack) next to the NavDisplay that renders it, then reload.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Back stack · ${snapshot.entries.size} ${if (snapshot.entries.size == 1) "entry" else "entries"}",
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedButton(
                onClick = { onApplyOperation(NavBackStackOperation.Pop(count = 1)) },
                enabled = snapshot.entries.size > 1,
            ) {
                Text("Pop")
            }
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
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
    Card(
        colors = if (isCurrent) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("#$index", style = MaterialTheme.typography.labelLarge)
                Text(entry.typeName, style = MaterialTheme.typography.titleSmall)
                if (isCurrent) {
                    Text(
                        "current",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = entry.display,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            // The actions have to wrap: a plugin pane can be narrow, and a row that overflows
            // squeezes the last label into one character per line instead of moving it down.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isCurrent) {
                    TextButton(onClick = { onApplyOperation(NavBackStackOperation.PopTo(index = index, inclusive = false)) }) {
                        Text("Pop to here")
                    }
                    TextButton(onClick = { onApplyOperation(NavBackStackOperation.MoveToTop(index = index)) }) {
                        Text("To top")
                    }
                }
                TextButton(
                    onClick = { onApplyOperation(NavBackStackOperation.RemoveAt(index = index)) },
                    enabled = canRemove,
                ) {
                    Text("Remove")
                }
                entry.key?.let { key ->
                    TextButton(onClick = { onCopyKeyToEditor(key) }) { Text("Copy to editor") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Push a NavKey", style = MaterialTheme.typography.titleSmall)

        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            // A placeholder rather than a floating label: the editor is filled programmatically
            // while unfocused, and a label that only lifts on focus would sit over the first line.
            placeholder = { Text("{\"type\": \"…\"}") },
            isError = editorError != null,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            minLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        editorError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { withParsedKey { onApplyOperation(NavBackStackOperation.Push(key = it, index = null)) } },
                enabled = draft.isNotBlank(),
            ) {
                Text("Push")
            }
            OutlinedButton(
                onClick = { withParsedKey { onApplyOperation(NavBackStackOperation.ReplaceAll(keys = listOf(it))) } },
                enabled = draft.isNotBlank(),
            ) {
                Text("Replace stack")
            }
        }

        HorizontalDivider()

        if (keyTypes.isEmpty()) {
            Text(
                "The app exposed no constructible key types. You can still copy an existing entry's key with \"Copy to editor\" and edit it.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text("Key types · click one to fill the editor", style = MaterialTheme.typography.titleSmall)
            keyTypes.forEach { type ->
                OutlinedCard(
                    onClick = {
                        onDraftChange(PrettyJson.encodeToString(JsonElement.serializer(), type.template))
                        editorError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(type.serialName, style = MaterialTheme.typography.bodyMedium)
                        if (type.fields.isNotEmpty()) {
                            Text(
                                type.fields.joinToString { field ->
                                    buildString {
                                        append(field.name)
                                        append(": ")
                                        append(field.type)
                                        if (field.optional) append(" = …")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseNavKey(text: String): JsonObject? = try {
    Json.parseToJsonElement(text) as? JsonObject
} catch (_: SerializationException) {
    null
}
