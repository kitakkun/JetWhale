package com.kitakkun.jetwhale.tools.mcpworkflow

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordingExportTest {
    private val calls = listOf(
        call(tool = "listSessions", arguments = "{}", result = """[{"sessionId":"s-8f2c","appName":"Demo"}]""", readOnly = true),
        call(tool = "getPluginStatus", arguments = """{"sessionId":"s-8f2c"}""", result = """{"ready":true}""", readOnly = true),
        call(
            tool = "findNodes",
            arguments = """{"sessionId":"s-8f2c","text":"Settings"}""",
            result = """{"nodes":[{"id":1361,"text":"Profile"},{"id":1377,"text":"Settings"}]}""",
            readOnly = true,
        ),
        call(tool = "performNodeAction", arguments = """{"sessionId":"s-8f2c","nodeId":1377,"action":"OnClick"}""", result = """{"applied":true}""", readOnly = false),
    )

    @Test
    fun `a value an earlier result returned becomes a saved variable`() {
        val workflow = exportWorkflow(calls, options(dropReads = false, parameters = emptyMap()))

        assertEquals(mapOf("sessionId" to "$[?(@.appName == 'Demo')].first().sessionId"), workflow.steps[0].save)
        assertEquals(JsonPrimitive("\${sessionId}"), workflow.steps[3].args["sessionId"])
    }

    @Test
    fun `a list item is found again by an identifying field rather than its index`() {
        val workflow = exportWorkflow(calls, options(dropReads = false, parameters = emptyMap()))

        assertEquals(mapOf("nodeId" to "$.nodes[?(@.text == 'Settings')].first().id"), workflow.steps[2].save)
        assertEquals(JsonPrimitive("\${nodeId}"), workflow.steps[3].args["nodeId"])
    }

    @Test
    fun `dropReads keeps only reads whose results are used`() {
        val workflow = exportWorkflow(calls, options(dropReads = true, parameters = emptyMap()))

        assertEquals(listOf("listSessions", "findNodes", "performNodeAction"), workflow.steps.map(Step::call))
    }

    @Test
    fun `a named literal becomes an input`() {
        val workflow = exportWorkflow(calls, options(dropReads = false, parameters = mapOf("label" to JsonPrimitive("Settings"))))

        assertEquals(JsonPrimitive("\${label}"), workflow.steps[2].args["text"])
        assertEquals(JsonPrimitive("Settings"), workflow.inputs.getValue("label").default)
    }

    @Test
    fun `an id nothing returned becomes an input flagged as likely to change`() {
        val workflow = exportWorkflow(
            listOf(call(tool = "getAccount", arguments = """{"accountId":"acc-1001"}""", result = "{}", readOnly = true)),
            options(dropReads = false, parameters = emptyMap()),
        )

        assertEquals(JsonPrimitive("\${accountId}"), workflow.steps.single().args["accountId"])
        assertTrue("likely different" in workflow.inputs.getValue("accountId").description.orEmpty())
    }

    @Test
    fun `an exported workflow validates and reads back`() {
        val workflow = exportWorkflow(calls, options(dropReads = true, parameters = emptyMap()))

        assertEquals(emptyList(), validate(workflow, setOf("app")))
        assertEquals(workflow, parseWorkflow(workflowToYaml(workflow), source = "exported.yaml"))
    }

    @Test
    fun `reading verbs mark a tool read-only unless the server says otherwise`() {
        assertTrue(isReadOnlyTool("com.example.getBackStack", readOnlyHint = null))
        assertEquals(false, isReadOnlyTool("jetwhale.click", readOnlyHint = null))
        assertEquals(false, isReadOnlyTool("getOrCreate", readOnlyHint = false))
    }

    private fun call(tool: String, arguments: String, result: String, readOnly: Boolean) = RecordedCall(
        server = "app",
        tool = tool,
        arguments = Json.parseToJsonElement(arguments).jsonObject,
        document = Json.parseToJsonElement(result),
        isError = false,
        readOnly = readOnly,
    )

    private fun options(dropReads: Boolean, parameters: Map<String, JsonPrimitive>) = ExportOptions(name = "Flow", description = null, dropReads = dropReads, parameters = parameters, multipleServers = false)
}
