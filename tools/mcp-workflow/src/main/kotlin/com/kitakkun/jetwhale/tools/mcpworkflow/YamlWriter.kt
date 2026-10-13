package com.kitakkun.jetwhale.tools.mcpworkflow

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

private val DefaultsOmittingJson = Json { encodeDefaults = false }

/** Writes [workflow] as block-style YAML, leaving out every field that has its default value. */
fun workflowToYaml(workflow: Workflow): String = buildString {
    appendLine("# Written by mcp-workflow. Edit freely; comments are kept only until the next export.")
    writeObject(DefaultsOmittingJson.encodeToJsonElement(workflow) as JsonObject, indent = 0)
}

private fun StringBuilder.writeObject(value: JsonObject, indent: Int) {
    value.forEach { (key, child) ->
        append(" ".repeat(indent)).append(scalarText(JsonPrimitive(key))).append(':')
        writeValueAfterKey(child, indent)
    }
}

private fun StringBuilder.writeValueAfterKey(value: JsonElement, indent: Int) {
    when {
        value is JsonObject && value.isNotEmpty() -> {
            appendLine()
            writeObject(value, indent + 2)
        }

        value is JsonArray && value.isNotEmpty() -> {
            appendLine()
            writeArray(value, indent)
        }

        else -> appendLine(" ${inlineText(value)}")
    }
}

private fun StringBuilder.writeArray(value: JsonArray, indent: Int) {
    value.forEach { item ->
        append(" ".repeat(indent)).append("- ")
        when {
            item is JsonObject && item.isNotEmpty() -> {
                val entries = item.entries.toList()
                entries.forEachIndexed { index, (key, child) ->
                    if (index > 0) append(" ".repeat(indent + 2))
                    append(scalarText(JsonPrimitive(key))).append(':')
                    writeValueAfterKey(child, indent + 2)
                }
            }

            else -> appendLine(inlineText(item))
        }
    }
}

private fun inlineText(value: JsonElement): String = when (value) {
    is JsonObject -> "{}"
    is JsonArray -> "[]"
    is JsonPrimitive -> scalarText(value)
    is JsonNull -> "null"
}

// YAML indicators such as `@`, `*` and `#` cannot start a plain scalar.
private val PlainSafe = Regex("""^[A-Za-z_$/][A-Za-z0-9_$./@{}\-]*$""")
private val WordsYamlReadsAsBooleanOrNull = setOf("true", "false", "null", "yes", "no", "on", "off", "~")

private fun scalarText(value: JsonPrimitive): String {
    if (!value.isString) return value.content
    val text = value.content
    // A JSON string literal is a valid YAML double-quoted scalar.
    return if (PlainSafe.matches(text) && text.lowercase() !in WordsYamlReadsAsBooleanOrNull) text else Json.encodeToString(JsonPrimitive.serializer(), value)
}
