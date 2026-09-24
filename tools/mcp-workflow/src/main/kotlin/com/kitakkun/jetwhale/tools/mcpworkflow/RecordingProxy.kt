package com.kitakkun.jetwhale.tools.mcpworkflow

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.util.Collections

/**
 * An MCP server that stands in front of real servers: it lists their tools as its own, forwards every
 * call unchanged, and records each call with its result.
 *
 * It adds three tools of its own so the agent being recorded can manage the recording:
 * `workflow_recording_status`, `workflow_recording_clear` and `workflow_recording_save`.
 *
 * @param servers The upstream servers, in the order their tools are listed. With more than one, each
 *   tool is listed as `<server>__<tool>` so the names stay unique.
 * @param defaultOutput Where the recording is written when the client disconnects; null writes nothing.
 */
class RecordingProxy(
    private val caller: ToolCaller,
    private val servers: List<String>,
    private val defaultOutput: File?,
    private val defaultName: String,
) {
    private val calls: MutableList<RecordedCall> = Collections.synchronizedList(mutableListOf())
    private val prefixed = servers.size > 1

    suspend fun createServer(): Server {
        val server = newToolServer("mcp-workflow-recorder")
        servers.forEach { upstream ->
            caller.listTools(upstream).forEach { tool ->
                val listedName = if (prefixed) "${upstream}__${tool.name}" else tool.name
                val readOnly = isReadOnlyTool(tool.name, tool.annotations?.readOnlyHint)
                server.addTool(tool.copy(name = listedName)) { request ->
                    val arguments = request.params.arguments ?: JsonObject(emptyMap())
                    val result = caller.callTool(upstream, tool.name, arguments)
                    calls += RecordedCall(upstream, tool.name, arguments, resultDocument(result), result.isError == true, readOnly)
                    result
                }
            }
        }
        addControlTools(server)
        return server
    }

    /** Writes the recording to [defaultOutput], if there is one and anything was recorded. */
    fun writeDefault() {
        val output = defaultOutput ?: return
        if (calls.isEmpty()) return
        output.writeText(workflowToYaml(export(defaultName, description = null, dropReads = false, parameters = emptyMap())))
        System.err.println("mcp-workflow: wrote ${calls.size} recorded calls to ${output.path}")
    }

    private fun export(name: String, description: String?, dropReads: Boolean, parameters: Map<String, JsonElement>): Workflow = exportWorkflow(calls.toList(), ExportOptions(name, description, dropReads, parameters, multipleServers = prefixed))

    private fun addControlTools(server: Server) {
        server.addTool(
            name = "workflow_recording_status",
            description = "Lists the tool calls recorded so far through this proxy.",
            inputSchema = ToolSchema(),
        ) {
            text(
                buildJsonObject {
                    put("recordedCalls", calls.size)
                    put("calls", JsonArray(calls.mapIndexed { index, call -> JsonPrimitive("${index + 1}. ${call.server}: ${call.tool}${if (call.isError) " (error)" else ""}") }))
                }.toString(),
            )
        }
        server.addTool(
            name = "workflow_recording_clear",
            description = "Forgets every call recorded so far, to start the recording of a flow from here.",
            inputSchema = ToolSchema(),
        ) {
            val dropped = calls.size
            calls.clear()
            text("""{"cleared":$dropped}""")
        }
        server.addTool(
            name = "workflow_recording_save",
            description = "Saves the recorded calls as a replayable workflow file. Values an earlier call returned and a later call used " +
                "become saved variables, so the workflow works in a fresh run. Returns the workflow written.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "File to write, e.g. workflows/login.yaml")
                    }
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Workflow name")
                    }
                    putJsonObject("description") {
                        put("type", "string")
                        put("description", "What the flow checks")
                    }
                    putJsonObject("dropReads") {
                        put("type", "boolean")
                        put("description", "Leave out read-only calls whose results no later call uses")
                    }
                    putJsonObject("parameters") {
                        put("type", "object")
                        put("description", "Recorded literal values to turn into workflow inputs, as input name to value")
                    }
                },
                required = listOf("path", "name"),
            ),
        ) { request ->
            val arguments = request.params.arguments ?: JsonObject(emptyMap())
            val path = arguments["path"]?.jsonPrimitive?.content ?: return@addTool text("""{"error":"path is required"}""", isError = true)
            val name = arguments["name"]?.jsonPrimitive?.content ?: return@addTool text("""{"error":"name is required"}""", isError = true)
            val yaml = workflowToYaml(
                export(
                    name = name,
                    description = arguments["description"]?.jsonPrimitive?.content,
                    dropReads = arguments["dropReads"]?.jsonPrimitive?.booleanOrNull == true,
                    parameters = (arguments["parameters"] as? JsonObject).orEmpty(),
                ),
            )
            File(path).apply { absoluteFile.parentFile?.mkdirs() }.writeText(yaml)
            text(yaml)
        }
    }
}

private fun text(value: String, isError: Boolean = false): CallToolResult = CallToolResult(content = listOf(TextContent(value)), isError = isError)
