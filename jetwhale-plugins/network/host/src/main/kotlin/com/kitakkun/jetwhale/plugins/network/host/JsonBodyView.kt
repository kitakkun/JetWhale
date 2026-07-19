package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Renders an HTTP body. JSON is syntax-highlighted and can be browsed either as a collapsible
 * accordion tree or as formatted text; non-JSON bodies are shown verbatim.
 */
@Composable
internal fun BodyBlock(label: String, body: String?, truncated: Boolean) {
    if (body.isNullOrEmpty()) return
    val json = remember(body) { runCatching { lenientJson.parseToJsonElement(body) }.getOrNull() }
    var mode by remember(body) { mutableStateOf(if (json != null) BodyMode.Tree else BodyMode.Raw) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (json != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = mode == BodyMode.Tree,
                    onClick = { mode = BodyMode.Tree },
                    label = { Text("Tree") },
                )
                FilterChip(
                    selected = mode == BodyMode.Raw,
                    onClick = { mode = BodyMode.Raw },
                    label = { Text("Raw") },
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.padding(10.dp)) {
                val colors = rememberJsonColors()
                if (json != null && mode == BodyMode.Tree) {
                    Column { JsonTreeNode(json, label = label, colors = colors) }
                } else {
                    val rendered = remember(json, body, colors) { json?.let { highlightedJson(it, colors) } }
                    Text(
                        text = rendered ?: AnnotatedString(body + if (truncated) "\n… (truncated)" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

private enum class BodyMode { Tree, Raw }

// ---------------------------------------------------------------------------------------
// Accordion tree
// ---------------------------------------------------------------------------------------

@Composable
private fun JsonTreeNode(element: JsonElement, label: String?, colors: JsonColors, depth: Int = 0) {
    when (element) {
        is JsonObject -> JsonBranch(
            label = label,
            isArray = false,
            size = element.size,
            depth = depth,
            colors = colors,
        ) {
            element.forEach { (key, value) -> JsonTreeNode(value, key, colors, depth + 1) }
        }

        is JsonArray -> JsonBranch(
            label = label,
            isArray = true,
            size = element.size,
            depth = depth,
            colors = colors,
        ) {
            element.forEachIndexed { index, value -> JsonTreeNode(value, "[$index]", colors, depth + 1) }
        }

        else -> JsonLeaf(label, element as JsonPrimitive, colors, depth)
    }
}

@Composable
private fun JsonBranch(
    label: String?,
    isArray: Boolean,
    size: Int,
    depth: Int,
    colors: JsonColors,
    children: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(depth < 1) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
            .clickable { expanded = !expanded }
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (expanded) "▾" else "▸",
            color = colors.punctuation,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            buildAnnotatedString {
                if (label != null) withStyle(SpanStyle(color = colors.key)) { append(label) }
                withStyle(SpanStyle(color = colors.punctuation)) {
                    append(if (isArray) "  [$size]" else "  {$size}")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
    if (expanded) children()
}

@Composable
private fun JsonLeaf(label: String?, primitive: JsonPrimitive, colors: JsonColors, depth: Int) {
    Text(
        buildAnnotatedString {
            if (label != null) {
                withStyle(SpanStyle(color = colors.key)) { append(label) }
                withStyle(SpanStyle(color = colors.punctuation)) { append(": ") }
            }
            append(primitiveAnnotated(primitive, colors))
        },
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(start = (depth * 16 + 16).dp, top = 1.dp, bottom = 1.dp),
    )
}

// ---------------------------------------------------------------------------------------
// Raw (syntax-highlighted, pretty-printed)
// ---------------------------------------------------------------------------------------

private fun highlightedJson(element: JsonElement, colors: JsonColors): AnnotatedString = buildAnnotatedString { appendJson(element, 0, colors) }

private fun AnnotatedString.Builder.appendJson(element: JsonElement, indent: Int, colors: JsonColors) {
    val pad = "  ".repeat(indent)
    val pad1 = "  ".repeat(indent + 1)
    fun punct(text: String) = withStyle(SpanStyle(color = colors.punctuation)) { append(text) }
    when (element) {
        is JsonObject -> {
            if (element.isEmpty()) {
                punct("{}")
                return
            }
            punct("{\n")
            element.entries.forEachIndexed { index, (key, value) ->
                append(pad1)
                withStyle(SpanStyle(color = colors.key)) { append("\"$key\"") }
                punct(": ")
                appendJson(value, indent + 1, colors)
                if (index < element.size - 1) punct(",")
                append("\n")
            }
            append(pad)
            punct("}")
        }

        is JsonArray -> {
            if (element.isEmpty()) {
                punct("[]")
                return
            }
            punct("[\n")
            element.forEachIndexed { index, value ->
                append(pad1)
                appendJson(value, indent + 1, colors)
                if (index < element.size - 1) punct(",")
                append("\n")
            }
            append(pad)
            punct("]")
        }

        else -> append(primitiveAnnotated(element as JsonPrimitive, colors))
    }
}

private fun primitiveAnnotated(primitive: JsonPrimitive, colors: JsonColors): AnnotatedString = buildAnnotatedString {
    val color = when {
        primitive is JsonNull -> colors.keyword
        primitive.isString -> colors.string
        primitive.content == "true" || primitive.content == "false" -> colors.keyword
        else -> colors.number
    }
    withStyle(SpanStyle(color = color)) { append(primitive.toString()) }
}

private data class JsonColors(
    val key: Color,
    val string: Color,
    val number: Color,
    val keyword: Color,
    val punctuation: Color,
)

@Composable
private fun rememberJsonColors(): JsonColors {
    val dark = isDarkTheme()
    val punctuation = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(dark, punctuation) {
        JsonColors(
            key = if (dark) Color(0xFF4FC3F7) else Color(0xFF0B6E99),
            string = if (dark) Color(0xFF81C784) else Color(0xFF2E7D32),
            number = if (dark) Color(0xFF64B5F6) else Color(0xFF1565C0),
            keyword = if (dark) Color(0xFFCE93D8) else Color(0xFF8E24AA),
            punctuation = punctuation,
        )
    }
}

private val lenientJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
}
