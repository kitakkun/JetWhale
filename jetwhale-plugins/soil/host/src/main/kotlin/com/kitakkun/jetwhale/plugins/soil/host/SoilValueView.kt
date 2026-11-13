package com.kitakkun.jetwhale.plugins.soil.host

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
import com.kitakkun.jetwhale.host.ui.JwPanel
import com.kitakkun.jetwhale.host.ui.JwProgressIndicator
import com.kitakkun.jetwhale.host.ui.JwSegmentedButtons
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTreeRow
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryValue
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilValueEncoding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val PrettyJson = Json { prettyPrint = true }

/**
 * How many children of one object or array the tree shows; a value can run to 256 K characters,
 * and the JSON view shows the rest.
 */
private const val TREE_CHILD_LIMIT = 200

/** The path of the root node in [JsonTreeLine.path]. */
@VisibleForTesting
internal const val JSON_ROOT_PATH = ""

/**
 * One line of a JSON value shown as a tree.
 *
 * @property path Where the node sits, as a JSON Pointer such as `/items/2/name`: what expansion is
 *   remembered by, unambiguous whatever the keys contain. The line standing for the children past
 *   the limit has its container's path with `#more` appended, which no node's path can be.
 * @property text The key or index, then the value of a primitive or the size of a container.
 */
@VisibleForTesting
internal data class JsonTreeLine(val path: String, val depth: Int, val text: String, val isExpandable: Boolean)

private enum class JsonValueMode(val label: String) { TREE("Tree"), JSON("JSON") }

/** The selected entry's value as the agent encoded it, and how. */
@Composable
internal fun SoilValueView(value: SoilValueLoad?) {
    when (value) {
        null, is SoilValueLoad.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small), verticalAlignment = Alignment.CenterVertically) {
            JwProgressIndicator()
            JwText(text = "Reading the value from the app…", color = JwTheme.colors.textSecondary)
        }

        is SoilValueLoad.Failed -> JwText(text = value.message, color = JwTheme.colors.error)

        is SoilValueLoad.Loaded -> when (val loaded = value.value) {
            is SoilEntryValue.NoReply -> JwText(text = "No reply yet.", color = JwTheme.colors.textSecondary)

            is SoilEntryValue.EntryGone -> JwText(text = "Soil no longer holds this entry, so its value is gone.", color = JwTheme.colors.textSecondary)

            is SoilEntryValue.Json -> Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
                JwText(text = loaded.encoding.description, style = JwTheme.textStyles.bodySmall, color = JwTheme.colors.textSecondary)
                JsonValue(loaded.json)
            }

            is SoilEntryValue.Text -> Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
                val truncationNote = if (loaded.isTruncated) " Cut at ${loaded.text.length} of ${loaded.fullLength} characters." else ""
                JwText(text = loaded.encoding.description + truncationNote, style = JwTheme.textStyles.bodySmall, color = JwTheme.colors.textSecondary)
                JwCodeBlock(text = loaded.text + if (loaded.isTruncated) "\n…" else "", wrap = true, copyLabel = "Copy value")
            }
        }
    }
}

@Composable
private fun JsonValue(json: JsonElement) {
    var mode by remember { mutableStateOf(JsonValueMode.TREE) }
    var expandedPaths by remember(json) { mutableStateOf(setOf(JSON_ROOT_PATH)) }
    Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
        JwSegmentedButtons(options = JsonValueMode.entries, selected = mode, onSelect = { mode = it }, label = JsonValueMode::label)
        when (mode) {
            JsonValueMode.TREE -> JwPanel(contentPadding = PaddingValues(JwSpacing.small)) {
                Column {
                    flattenJsonTree(json, expandedPaths).forEach { line ->
                        val toggle = { expandedPaths = if (line.path in expandedPaths) expandedPaths - line.path else expandedPaths + line.path }
                        JwTreeRow(
                            text = line.text,
                            depth = line.depth,
                            expandable = line.isExpandable,
                            expanded = line.path in expandedPaths,
                            selected = false,
                            onClick = { if (line.isExpandable) toggle() },
                            onToggleExpanded = toggle,
                        )
                    }
                }
            }

            JsonValueMode.JSON -> JwCodeBlock(text = remember(json) { PrettyJson.encodeToString(JsonElement.serializer(), json) }, wrap = true, copyLabel = "Copy value")
        }
    }
}

private val SoilValueEncoding.description: String
    get() = when (this) {
        SoilValueEncoding.REGISTERED_SERIALIZER -> "Encoded with the serializer the app registered."
        SoilValueEncoding.CLASS_SERIALIZERS -> "Encoded with the serializers of the value's own classes."
        SoilValueEncoding.TO_STRING -> "Shown with toString(): no serializer was found for this value. Register one with SoilValueSerializers to see it as JSON."
    }

/** The lines of [json] shown as a tree with the containers at [expandedPaths] open. */
@VisibleForTesting
internal fun flattenJsonTree(json: JsonElement, expandedPaths: Set<String>): List<JsonTreeLine> = buildList {
    fun addNode(element: JsonElement, label: String?, path: String, depth: Int) {
        val prefix = label?.let { "$it: " }.orEmpty()
        when (element) {
            is JsonObject -> {
                add(JsonTreeLine(path = path, depth = depth, text = "$prefix{${element.size}}", isExpandable = element.isNotEmpty()))
                if (path in expandedPaths) {
                    element.entries.take(TREE_CHILD_LIMIT).forEach { (key, child) -> addNode(child, key, "$path/${key.replace("~", "~0").replace("/", "~1")}", depth + 1) }
                    addRemainderLine(element.size, path, depth + 1)
                }
            }

            is JsonArray -> {
                add(JsonTreeLine(path = path, depth = depth, text = "$prefix[${element.size}]", isExpandable = element.isNotEmpty()))
                if (path in expandedPaths) {
                    element.take(TREE_CHILD_LIMIT).forEachIndexed { index, child -> addNode(child, "[$index]", "$path/$index", depth + 1) }
                    addRemainderLine(element.size, path, depth + 1)
                }
            }

            is JsonPrimitive -> add(JsonTreeLine(path = path, depth = depth, text = prefix + element.toString(), isExpandable = false))
        }
    }
    addNode(json, label = null, path = JSON_ROOT_PATH, depth = 0)
}

/** The line standing for the children past the limit of a container with [childCount] of them, if there are any. */
private fun MutableList<JsonTreeLine>.addRemainderLine(childCount: Int, path: String, depth: Int) {
    if (childCount > TREE_CHILD_LIMIT) add(JsonTreeLine(path = "$path#more", depth = depth, text = "… ${childCount - TREE_CHILD_LIMIT} more, shown in the JSON view", isExpandable = false))
}
