package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Renders an HTTP body. JSON bodies can be shown either as an expandable tree or as
 * pretty-printed text; non-JSON bodies are shown verbatim.
 */
@Composable
internal fun BodyBlock(body: String?, truncated: Boolean) {
    if (body.isNullOrEmpty()) return
    val json = remember(body) { runCatching { lenientJson.parseToJsonElement(body) }.getOrNull() }
    var treeMode by remember(body) { mutableStateOf(json != null) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (json != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(treeMode, onClick = { treeMode = true }, label = { Text("Tree") })
                FilterChip(!treeMode, onClick = { treeMode = false }, label = { Text("JSON") })
            }
        }
        Card(Modifier.fillMaxWidth()) {
            if (json != null && treeMode) {
                Column(Modifier.padding(8.dp)) { JsonTreeNode(json) }
            } else {
                val text = json?.let { prettyJson.encodeToString(JsonElement.serializer(), it) } ?: body
                Text(
                    text + if (truncated) "\n… (truncated)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun JsonTreeNode(element: JsonElement, label: String? = null) {
    val prefix = label?.let { "$it: " } ?: ""
    when (element) {
        is JsonObject -> {
            var expanded by remember { mutableStateOf(true) }
            Text(
                "$prefix{${if (expanded) "" else "… ${element.size}"}}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { expanded = !expanded },
            )
            if (expanded) {
                Column(Modifier.padding(start = 14.dp)) {
                    element.forEach { (key, value) -> JsonTreeNode(value, key) }
                }
            }
        }

        is JsonArray -> {
            var expanded by remember { mutableStateOf(true) }
            Text(
                "$prefix[${if (expanded) "" else "… ${element.size}"}]",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { expanded = !expanded },
            )
            if (expanded) {
                Column(Modifier.padding(start = 14.dp)) {
                    element.forEachIndexed { index, value -> JsonTreeNode(value, index.toString()) }
                }
            }
        }

        else -> Text("$prefix$element", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

private val lenientJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

private val prettyJson = Json {
    prettyPrint = true
    isLenient = true
}
