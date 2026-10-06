package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpContent
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpParameterDescriptor
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpToolDescriptor
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

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
    properties = JsonObject(
        leadingProperties + parameters.mapValues { (_, parameter) ->
            JsonObject(parameter.schema + ("description" to JsonPrimitive(parameter.description)))
        },
    ),
    required = leadingProperties.keys.toList() + parameters.filterValues(JetWhaleMcpParameterDescriptor::required).keys,
)

/**
 * A plugin's result as the MCP wire type. Plugins never see the MCP library's types, so this is the
 * one place where the SDK's vocabulary and the protocol's meet.
 */
fun JetWhaleMcpResult.toCallToolResult(): CallToolResult = CallToolResult(
    content = content.map { block ->
        when (block) {
            is JetWhaleMcpContent.Text -> TextContent(block.text)
            is JetWhaleMcpContent.Image -> ImageContent(data = Base64.encode(block.data), mimeType = block.mimeType)
        }
    },
    isError = isError,
    structuredContent = structuredContent,
)

fun errorResult(message: String): CallToolResult = JetWhaleMcpResult.error(message).toCallToolResult()

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
