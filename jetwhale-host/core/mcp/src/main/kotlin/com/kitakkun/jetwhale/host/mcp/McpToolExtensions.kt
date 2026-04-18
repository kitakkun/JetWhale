package com.kitakkun.jetwhale.host.mcp

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun errorResult(message: String): CallToolResult = CallToolResult(
    content = listOf(TextContent(buildJsonObject { put("error", message) }.toString())),
    isError = true,
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
