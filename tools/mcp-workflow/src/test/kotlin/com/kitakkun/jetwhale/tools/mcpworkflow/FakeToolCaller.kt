package com.kitakkun.jetwhale.tools.mcpworkflow

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject

/** Answers tool calls with [handler] and remembers every call. */
class FakeToolCaller(private val handler: suspend (server: String, tool: String, arguments: JsonObject) -> CallToolResult) : ToolCaller {
    private val recorded = mutableListOf<Triple<String, String, JsonObject>>()
    val calls: List<Triple<String, String, JsonObject>> get() = recorded

    override suspend fun listTools(server: String): List<Tool> = listOf(Tool(name = "anything", inputSchema = ToolSchema()))

    override suspend fun callTool(server: String, tool: String, arguments: JsonObject): CallToolResult {
        recorded += Triple(server, tool, arguments)
        return handler(server, tool, arguments)
    }

    override suspend fun close() = Unit
}

fun textResult(text: String, isError: Boolean): CallToolResult = CallToolResult(content = listOf(TextContent(text)), isError = isError)
