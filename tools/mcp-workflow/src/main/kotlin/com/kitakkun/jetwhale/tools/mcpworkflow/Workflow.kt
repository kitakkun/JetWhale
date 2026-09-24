package com.kitakkun.jetwhale.tools.mcpworkflow

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The format version this build reads and writes; a file declaring any other is refused. */
const val WORKFLOW_FORMAT_VERSION: Int = 1

/**
 * A sequence of MCP tool calls, read from YAML (or JSON, which YAML contains).
 *
 * Optional fields carry defaults because they are optional in the file, not to paper over missing
 * arguments in code.
 *
 * @property servers MCP servers this workflow talks to, keyed by the name steps refer to them by.
 *   Merged over the servers of an `.mcp.json` passed on the command line, so a workflow can stay free
 *   of machine-specific ports.
 * @property inputs Parameters a caller passes in; in serve mode they become the tool's arguments.
 * @property vars Constants, templated against the inputs.
 * @property outputs Values reported after the run, templated against everything the steps saved.
 */
@Serializable
data class Workflow(
    val version: Int,
    val name: String,
    val description: String? = null,
    val servers: Map<String, ServerConfig> = emptyMap(),
    val inputs: Map<String, InputSpec> = emptyMap(),
    val vars: Map<String, JsonElement> = emptyMap(),
    val steps: List<Step>,
    val outputs: Map<String, JsonElement> = emptyMap(),
)

/**
 * How to reach an MCP server, in the shape `.mcp.json` uses: `command` (+ `args`, `env`) for stdio,
 * `url` with `type` `sse` or `http` (Streamable HTTP) for remote servers.
 */
@Serializable
data class ServerConfig(
    val type: String? = null,
    val url: String? = null,
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
)

/** A workflow parameter. [type] is a JSON Schema primitive type name. */
@Serializable
data class InputSpec(
    val type: String = "string",
    val description: String? = null,
    val default: JsonElement? = null,
)

/**
 * One tool call.
 *
 * @property server Which server to call; may be omitted when the workflow talks to exactly one.
 * @property save Values to keep for later steps: variable name to a path into the result.
 * @property wait Repeat the call until every expectation holds, or give up after the timeout — for
 *   state that settles asynchronously, instead of a fixed sleep.
 * @property retries Extra attempts after an error or a failed expectation, without waiting for a
 *   condition.
 * @property timeout Longest a single call may take, e.g. `10s`.
 * @property continueOnFailure Report the failure and go on with the next step instead of stopping.
 */
@Serializable
data class Step(
    val id: String? = null,
    val name: String? = null,
    val server: String? = null,
    val call: String,
    val args: JsonObject = JsonObject(emptyMap()),
    val save: Map<String, String> = emptyMap(),
    val expect: List<Expectation> = emptyList(),
    val wait: WaitSpec? = null,
    val retries: Int = 0,
    val timeout: String? = null,
    val continueOnFailure: Boolean = false,
)

@Serializable
data class WaitSpec(
    val timeout: String,
    val interval: String = "500ms",
)

/**
 * A check on a call's result. [path] selects a value in the result document (see [JsonPath]); every
 * other property that is set must hold for it. [error] checks whether the server reported a tool
 * error; a step with no expectation about [error] fails on a tool error.
 */
@Serializable
data class Expectation(
    val path: String = "$",
    val equals: JsonElement? = null,
    val notEquals: JsonElement? = null,
    val contains: JsonElement? = null,
    val matches: String? = null,
    val exists: Boolean? = null,
    val gt: Double? = null,
    val gte: Double? = null,
    val lt: Double? = null,
    val lte: Double? = null,
    val length: Int? = null,
    val error: Boolean? = null,
)
