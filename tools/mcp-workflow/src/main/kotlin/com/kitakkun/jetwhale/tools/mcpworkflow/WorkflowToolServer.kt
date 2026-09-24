package com.kitakkun.jetwhale.tools.mcpworkflow

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import kotlin.io.encoding.Base64

/** Images from the end of a run are returned inline, up to this many; the rest stay as files. */
private const val INLINE_IMAGE_LIMIT = 2

/**
 * Serves workflows as MCP tools: one tool per workflow, its inputs as the tool's arguments, and the
 * run's outcome — each step's status, the outputs, the images — as the result. An agent then runs a
 * whole checked flow in one call instead of re-deriving it call by call.
 *
 * @param files Workflow files, read afresh for every connection so edits show up on reconnect.
 */
class WorkflowToolServer(
    private val files: List<File>,
    private val runnerFor: (Workflow) -> WorkflowRunner,
) {
    fun createServer(): Server {
        val server = newToolServer("mcp-workflow")
        val taken = mutableSetOf<String>()
        files.forEach { file ->
            val workflow = readWorkflow(file)
            val name = toolNameFor(file, taken)
            server.addTool(name = name, description = describe(workflow, file), inputSchema = inputSchema(workflow)) { request ->
                val inputs = (request.params.arguments ?: JsonObject(emptyMap())).filterKeys(workflow.inputs::containsKey)
                val outcome = try {
                    runnerFor(workflow).run(workflow, inputs)
                } catch (e: WorkflowFormatException) {
                    return@addTool CallToolResult(content = listOf(TextContent(buildJsonObject { put("error", e.message) }.toString())), isError = true)
                }
                resultOf(outcome)
            }
        }
        return server
    }
}

internal fun toolNameFor(file: File, taken: MutableSet<String>): String {
    val base = "workflow_" + file.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_-]"), "_")
    var candidate = base
    var suffix = 2
    while (!taken.add(candidate)) candidate = "${base}_${suffix++}"
    return candidate
}

private fun describe(workflow: Workflow, file: File): String = buildString {
    append(workflow.description ?: workflow.name)
    append("\n\nRuns the workflow in ${file.name}: ")
    append(workflow.steps.mapIndexed(::stepLabel).joinToString(" → "))
    append(". The result reports each step as passed, failed or skipped, and isError is set when any step failed.")
}

internal fun inputSchema(workflow: Workflow): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        workflow.inputs.forEach { (name, spec) ->
            putJsonObject(name) {
                put("type", spec.type)
                spec.description?.let { put("description", it) }
                spec.default?.let { put("default", it) }
            }
        }
    },
    required = workflow.inputs.filterValues { it.default == null }.keys.toList(),
)

private fun resultOf(outcome: RunOutcome): CallToolResult {
    val summary = buildJsonObject {
        put("workflow", outcome.workflow)
        put("passed", outcome.passed)
        put("durationMs", outcome.duration.inWholeMilliseconds)
        put(
            "steps",
            JsonArray(
                outcome.steps.map { step ->
                    buildJsonObject {
                        put("step", step.label)
                        put("status", step.status.name.lowercase())
                        step.message?.let { put("message", it) }
                        if (step.attempts > 1) put("attempts", step.attempts)
                    }
                },
            ),
        )
        put("outputs", JsonObject(outcome.outputs))
        put("artifacts", JsonArray(outcome.steps.flatMap(StepOutcome::artifacts).map { JsonPrimitive(it.absolutePath) }))
    }
    val images = outcome.steps.flatMap(StepOutcome::artifacts).takeLast(INLINE_IMAGE_LIMIT).map { file ->
        ImageContent(data = Base64.encode(file.readBytes()), mimeType = "image/${file.extension}")
    }
    return CallToolResult(content = listOf(TextContent(summary.toString())) + images, isError = !outcome.passed)
}
