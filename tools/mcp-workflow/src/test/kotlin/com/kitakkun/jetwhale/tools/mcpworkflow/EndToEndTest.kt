package com.kitakkun.jetwhale.tools.mcpworkflow

import io.ktor.server.engine.EmbeddedServer
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/** Runs, records and serves against real MCP servers over SSE on loopback, all in this process. */
class EndToEndTest {
    private val embeddedServers = mutableListOf<EmbeddedServer<*, *>>()
    private val directory: File = Files.createTempDirectory("mcp-workflow-e2e").toFile()

    @AfterTest
    fun stopServersAndDeleteTempDirectory() {
        embeddedServers.forEach { it.stop(gracePeriodMillis = 0, timeoutMillis = 1000) }
        directory.deleteRecursively()
    }

    private val accountWorkflowYaml = """
        version: 1
        name: Account round trip
        inputs:
          email:
            default: qa@example.com
        steps:
          - id: create
            call: createAccount
            args: { email: "${'$'}{email}", name: QA }
            save: { accountId: $.id }
          - call: getAccount
            args: { id: "${'$'}{accountId}" }
            expect:
              - path: $.email
                equals: "${'$'}{email}"
        outputs:
          accountId: "${'$'}{accountId}"
    """.trimIndent()

    @Test
    fun `a workflow runs against a server over SSE`() = runBlocking {
        val servers = mapOf("accounts" to startDemoServer())
        val caller = McpServerConnections(servers)
        val runner = WorkflowRunner(caller, servers.keys, TimeSource.Monotonic, emptyMap(), artifactRootDirectory = null) {}

        val outcome = runner.run(parseWorkflow(accountWorkflowYaml, source = "flow.yaml"), inputs = emptyMap())
        caller.close()

        assertTrue(outcome.passed, outcome.steps.joinToString { "${it.label}: ${it.message}" })
        assertTrue(outcome.outputs.getValue("accountId").jsonPrimitive.content.startsWith("acc-"))
    }

    @Test
    fun `a recording through the proxy replays against a fresh server`() = runBlocking {
        val proxy = RecordingProxy(McpServerConnections(mapOf("accounts" to startDemoServer())), listOf("accounts"))
        val mcpClient = McpServerConnections(mapOf("proxy" to ServerConfig(type = "sse", url = sseUrl(serveSse(port = 0, wait = false, proxy::createServer)))))
        val created = mcpClient.callTool(
            "proxy",
            "createAccount",
            buildJsonObject {
                put("email", "rec@example.com")
                put("name", "Rec")
            },
        )
        val id = Json.parseToJsonElement((created.content.single() as TextContent).text).jsonObject.getValue("id")
        mcpClient.callTool("proxy", "getAccount", buildJsonObject { put("id", id) })
        val recordedWorkflowFile = File(directory, "recorded.yaml")
        mcpClient.callTool(
            "proxy",
            "workflow_recording_save",
            buildJsonObject {
                put("path", recordedWorkflowFile.path)
                put("name", "Recorded")
            },
        )
        mcpClient.close()

        val workflow = readWorkflow(recordedWorkflowFile)
        assertEquals(JsonPrimitive("\${id}"), workflow.steps[1].args["id"])
        val fresh = mapOf("accounts" to startDemoServer())
        val replay = WorkflowRunner(McpServerConnections(fresh), fresh.keys, TimeSource.Monotonic, emptyMap(), artifactRootDirectory = null) {}
        assertTrue(replay.run(workflow, inputs = emptyMap()).passed)
    }

    @Test
    fun `a served workflow is one tool whose result reports the run`() = runBlocking {
        val file = File(directory, "account-round-trip.yaml").apply { writeText(accountWorkflowYaml) }
        val servers = mapOf("accounts" to startDemoServer())
        val toolServer = WorkflowToolServer(listOf(file)) { workflow, inputs ->
            val caller = McpServerConnections(servers)
            try {
                WorkflowRunner(caller, servers.keys, TimeSource.Monotonic, emptyMap(), artifactRootDirectory = null) {}.run(workflow, inputs)
            } finally {
                caller.close()
            }
        }
        val mcpClient = McpServerConnections(mapOf("workflows" to ServerConfig(type = "sse", url = sseUrl(serveSse(port = 0, wait = false) { toolServer.createServer() }))))

        val tool = mcpClient.listTools("workflows").single()
        val result = mcpClient.callTool("workflows", tool.name, buildJsonObject { put("email", "served@example.com") })
        mcpClient.close()

        assertEquals("workflow_account-round-trip", tool.name)
        assertEquals(JsonPrimitive("qa@example.com"), (tool.inputSchema.properties?.get("email") as JsonObject)["default"])
        val summary = Json.parseToJsonElement((result.content.first() as TextContent).text).jsonObject
        assertTrue(summary.getValue("passed").jsonPrimitive.boolean, summary.toString())
    }

    private fun startDemoServer(): ServerConfig = ServerConfig(type = "sse", url = sseUrl(serveSse(port = 0, wait = false, ::demoServer)))

    private fun sseUrl(embeddedServer: EmbeddedServer<*, *>): String {
        embeddedServers += embeddedServer
        val port = runBlocking { embeddedServer.engine.resolvedConnectors().first().port }
        return "http://127.0.0.1:$port/sse"
    }
}
