package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpToolDescriptor
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Assembles the MCP input schema of a tool declared with the parameter DSL.
 *
 * [leadingProperties] are properties the host adds on top of the command's own parameters and are
 * always required — the plugin path uses it to prepend the `sessionId` that routes a call to the
 * right plugin instance, while host-scoped tools pass nothing.
 */
fun JetWhaleMcpToolDescriptor.toToolSchema(
    leadingProperties: Map<String, JsonObject> = emptyMap(),
): ToolSchema = ToolSchema(
    // The parameter's schema describes its type; only the description has to be merged in.
    properties = JsonObject(
        leadingProperties + parameters.mapValues { (_, parameter) ->
            JsonObject(parameter.schema + ("description" to JsonPrimitive(parameter.description)))
        },
    ),
    required = leadingProperties.keys.toList() + parameters.filterValues { it.required }.keys,
)

fun errorResult(message: String): CallToolResult = CallToolResult(
    content = listOf(TextContent(buildJsonObject { put("error", message) }.toString())),
    isError = true,
)

fun successResult(): CallToolResult = CallToolResult(
    content = listOf(TextContent(buildJsonObject { put("success", true) }.toString())),
)

fun stringProperty(description: String): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
}

fun numberProperty(description: String): JsonObject = buildJsonObject {
    put("type", "number")
    put("description", description)
}

val Any.jsonContent: String?
    get() = (this as? JsonPrimitive)?.content

val Any.jsonInt: Int?
    get() = (this as? JsonPrimitive)?.content?.toIntOrNull()

val Any.jsonFloat: Float?
    get() = (this as? JsonPrimitive)?.content?.toFloatOrNull()
