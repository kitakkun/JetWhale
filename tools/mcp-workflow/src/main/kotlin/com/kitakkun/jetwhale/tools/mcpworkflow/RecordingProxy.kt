package com.kitakkun.jetwhale.tools.mcpworkflow

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
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
                    calls += RecordedCall(
                        server = upstream,
                        tool = tool.name,
                        arguments = arguments,
                        document = resultDocument(result),
                        isError = result.isError == true,
                        readOnly = readOnly,
                        expectations = emptyList(),
                    )
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

    private fun export(name: String, description: String?, dropReads: Boolean, parameters: Map<String, JsonElement>): Workflow = exportWorkflow(synchronized(calls) { calls.toList() }, ExportOptions(name, description, dropReads, parameters, multipleServers = prefixed))

    private fun addControlTools(server: Server) {
        server.addTool(
            name = "workflow_recording_status",
            description = "Lists the tool calls recorded so far through this proxy.",
            inputSchema = ToolSchema(),
        ) {
            val lines = calls.mapIndexed { index, call -> JsonPrimitive("${index + 1}. ${call.server}: ${call.tool}${if (call.isError) " (error)" else ""}") }
            text(
                buildJsonObject {
                    put("recordedCalls", calls.size)
                    put("calls", JsonArray(lines))
                }.toString(),
                isError = false,
            )
        }
        server.addTool(
            name = "workflow_recording_clear",
            description = "Forgets every call recorded so far, to start the recording of a flow from here.",
            inputSchema = ToolSchema(),
        ) {
            val dropped = calls.size
            calls.clear()
            text("""{"cleared":$dropped}""", isError = false)
        }
        server.addTool(
            name = "workflow_recording_expect",
            description = "Attaches a check to the last recorded call, so the saved workflow verifies it on replay. Takes the " +
                "fields of a workflow expectation: path (default \"$\") plus one or more of equals, notEquals, contains, matches, " +
                "exists, gt, gte, lt, lte, length, error. Call it right after the call whose result it checks.",
            inputSchema = ExpectToolSchema,
        ) { request -> expectOnLast(request.params.arguments ?: JsonObject(emptyMap())) }
        server.addTool(
            name = "workflow_recording_save",
            description = "Saves the recorded calls as a replayable workflow file. Values an earlier call returned and a later call used " +
                "become saved variables, so the workflow works in a fresh run. Returns the workflow written.",
            inputSchema = SaveToolSchema,
        ) { request -> save(request.params.arguments ?: JsonObject(emptyMap())) }
    }

    private fun expectOnLast(arguments: JsonObject): CallToolResult {
        val expectation = try {
            WorkflowJson.decodeFromJsonElement(Expectation.serializer(), arguments)
        } catch (e: SerializationException) {
            return text(buildJsonObject { put("error", "not an expectation: ${e.message}") }.toString(), isError = true)
        }
        val failure = synchronized(calls) {
            val last = calls.lastOrNull() ?: return text("""{"error":"nothing has been recorded yet"}""", isError = true)
            calls[calls.lastIndex] = last.copy(expectations = last.expectations + expectation)
            failureOf(expectation, last.document, last.isError)
        }
        // A check that already fails on the recorded result would fail every replay; say so now.
        return text(
            buildJsonObject {
                put("attached", true)
                failure?.let { put("warning", "fails on the recorded result: $it") }
            }.toString(),
            isError = false,
        )
    }

    private fun save(arguments: JsonObject): CallToolResult {
        val path = arguments["path"]?.jsonPrimitive?.content ?: return text("""{"error":"path is required"}""", isError = true)
        val name = arguments["name"]?.jsonPrimitive?.content ?: return text("""{"error":"name is required"}""", isError = true)
        val yaml = workflowToYaml(
            export(
                name = name,
                description = arguments["description"]?.jsonPrimitive?.content,
                dropReads = arguments["dropReads"]?.jsonPrimitive?.booleanOrNull == true,
                parameters = (arguments["parameters"] as? JsonObject).orEmpty(),
            ),
        )
        File(path).apply { absoluteFile.parentFile?.mkdirs() }.writeText(yaml)
        return text(yaml, isError = false)
    }
}

private val ExpectToolSchema = ToolSchema(
    properties = buildJsonObject {
        property(name = "path", type = "string", description = "Path into the result, e.g. $.stacks[0].entries[-1].typeName")
        putJsonObject("equals") { put("description", "Value the path must equal (any JSON)") }
        putJsonObject("contains") { put("description", "Element, key or substring the value must contain") }
        property(name = "matches", type = "string", description = "Regular expression the value must match")
        property(name = "exists", type = "boolean", description = "Whether the path must select something")
        putJsonObject("notEquals") { put("description", "Value the path must differ from (any JSON)") }
        property(name = "gt", type = "number", description = "Number the value must be greater than")
        property(name = "gte", type = "number", description = "Number the value must be at least")
        property(name = "lt", type = "number", description = "Number the value must be less than")
        property(name = "lte", type = "number", description = "Number the value must be at most")
        property(name = "length", type = "integer", description = "Length the array, object or string must have")
        property(name = "error", type = "boolean", description = "Whether the tool must have reported an error")
    },
    required = emptyList(),
)

private val SaveToolSchema = ToolSchema(
    properties = buildJsonObject {
        property(name = "path", type = "string", description = "File to write, e.g. workflows/login.yaml")
        property(name = "name", type = "string", description = "Workflow name")
        property(name = "description", type = "string", description = "What the flow checks")
        property(name = "dropReads", type = "boolean", description = "Leave out read-only calls whose results no later call uses")
        property(name = "parameters", type = "object", description = "Recorded literal values to turn into workflow inputs, as input name to value")
    },
    required = listOf("path", "name"),
)

private fun JsonObjectBuilder.property(name: String, type: String, description: String) {
    putJsonObject(name) {
        put("type", type)
        put("description", description)
    }
}

private fun text(value: String, isError: Boolean): CallToolResult = CallToolResult(content = listOf(TextContent(value)), isError = isError)
