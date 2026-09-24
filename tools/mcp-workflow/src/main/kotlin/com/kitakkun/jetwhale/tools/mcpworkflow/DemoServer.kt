package com.kitakkun.jetwhale.tools.mcpworkflow

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A small in-memory "accounts" MCP server, to try workflows that span more than one server without
 * installing anything: create, read, list and delete accounts. Ids are generated, so a recording of
 * it exercises the recorder's value tracking.
 */
fun demoServer(): Server {
    val accounts = ConcurrentHashMap<String, JsonObject>()
    val nextId = AtomicInteger(1000)
    val server = newToolServer("mcp-workflow-demo")
    server.addTool(
        name = "createAccount",
        description = "Creates an account and returns it with its generated id.",
        inputSchema = schema(required = listOf("email", "name"), "email" to "string", "name" to "string"),
    ) { request ->
        val args = request.params.arguments ?: JsonObject(emptyMap())
        val id = "acc-${nextId.getAndIncrement()}"
        val account = buildJsonObject {
            put("id", id)
            put("email", args["email"]?.jsonPrimitive?.content)
            put("name", args["name"]?.jsonPrimitive?.content)
        }
        accounts[id] = account
        json(account.toString())
    }
    server.addTool(
        name = "getAccount",
        description = "Returns the account with the given id.",
        inputSchema = schema(required = listOf("id"), "id" to "string"),
        toolAnnotations = ToolAnnotations(readOnlyHint = true),
    ) { request ->
        val id = request.params.arguments?.get("id")?.jsonPrimitive?.content
        accounts[id]?.let { json(it.toString()) } ?: CallToolResult(content = listOf(TextContent("""{"error":"no account $id"}""")), isError = true)
    }
    server.addTool(
        name = "listAccounts",
        description = "Lists every account.",
        inputSchema = ToolSchema(),
        toolAnnotations = ToolAnnotations(readOnlyHint = true),
    ) { json(buildJsonObject { put("accounts", JsonArray(accounts.values.toList())) }.toString()) }
    server.addTool(
        name = "deleteAccount",
        description = "Deletes the account with the given id.",
        inputSchema = schema(required = listOf("id"), "id" to "string"),
    ) { request ->
        val removed = accounts.remove(request.params.arguments?.get("id")?.jsonPrimitive?.content) != null
        json("""{"deleted":$removed}""")
    }
    return server
}

private fun schema(required: List<String>, vararg properties: Pair<String, String>): ToolSchema = ToolSchema(
    properties = buildJsonObject { properties.forEach { (name, type) -> putJsonObject(name) { put("type", type) } } },
    required = required,
)

private fun json(text: String): CallToolResult = CallToolResult(content = listOf(TextContent(text)))
