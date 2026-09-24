package com.kitakkun.jetwhale.tools.mcpworkflow

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.YamlTaggedNode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.File

internal val WorkflowJson: Json = Json {
    ignoreUnknownKeys = false
    prettyPrint = true
}

class WorkflowFormatException(message: String) : Exception(message)

/**
 * Reads a workflow file. YAML is the format written by the tool — it takes comments and diffs line
 * by line — and JSON is read as the YAML subset it is.
 *
 * Arguments are free-form JSON, which kaml cannot decode into, so the YAML tree is converted to JSON
 * first and the model decoded from that.
 */
fun readWorkflow(file: File): Workflow = parseWorkflow(file.readText(), source = file.path)

fun parseWorkflow(text: String, source: String): Workflow {
    val tree = try {
        YamlToJson(text.lines()).convert(Yaml.default.parseToYamlNode(text))
    } catch (e: Exception) {
        throw WorkflowFormatException("$source is not valid YAML: ${e.message}")
    }
    val version = (tree as? JsonObject)?.get("version")?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() }
        ?: throw WorkflowFormatException("$source has no `version`; this tool reads version $WORKFLOW_FORMAT_VERSION")
    if (version != WORKFLOW_FORMAT_VERSION) {
        throw WorkflowFormatException("$source is version $version; this tool reads version $WORKFLOW_FORMAT_VERSION")
    }
    return try {
        WorkflowJson.decodeFromJsonElement<Workflow>(tree)
    } catch (e: SerializationException) {
        throw WorkflowFormatException("$source does not describe a workflow: ${e.message}")
    }
}

/** Reads the `mcpServers` of an `.mcp.json`. */
fun readMcpConfig(file: File): Map<String, ServerConfig> {
    val servers = Json.parseToJsonElement(file.readText()).jsonObject["mcpServers"] as? JsonObject
        ?: throw WorkflowFormatException("${file.path} has no `mcpServers` object")
    val lenient = Json { ignoreUnknownKeys = true }
    return servers.mapValues { (_, config) -> lenient.decodeFromJsonElement<ServerConfig>(config) }
}

/**
 * Converts a YAML tree to JSON. A plain scalar becomes a boolean or number when it reads as one, as
 * YAML itself would type it; a quoted scalar always stays a string, which is how a file keeps `"8080"`
 * a string. kaml keeps each scalar's location but not its quoting, so the quote is read from [lines].
 */
private class YamlToJson(private val lines: List<String>) {
    fun convert(node: YamlNode): JsonElement = when (node) {
        is YamlNull -> JsonNull
        is YamlScalar -> scalar(node)
        is YamlList -> JsonArray(node.items.map(::convert))
        is YamlMap -> JsonObject(node.entries.entries.associate { (key, value) -> key.content to convert(value) })
        is YamlTaggedNode -> convert(node.innerNode)
    }

    private fun scalar(scalar: YamlScalar): JsonElement {
        val content = scalar.content
        val location = scalar.location
        val firstChar = lines.getOrNull(location.line - 1)?.getOrNull(location.column - 1)
        if (firstChar == '"' || firstChar == '\'') return JsonPrimitive(content)
        return when {
            content == "true" || content == "false" -> JsonPrimitive(content.toBoolean())
            content.toLongOrNull() != null -> JsonPrimitive(content.toLong())
            content.toDoubleOrNull() != null && content.any(Char::isDigit) -> JsonPrimitive(content.toDouble())
            else -> JsonPrimitive(content)
        }
    }
}
