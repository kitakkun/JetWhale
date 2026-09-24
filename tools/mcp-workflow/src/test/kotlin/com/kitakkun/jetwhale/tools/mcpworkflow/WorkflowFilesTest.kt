package com.kitakkun.jetwhale.tools.mcpworkflow

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkflowFilesTest {
    @Test
    fun `a YAML workflow reads with typed scalars and quoted strings kept as strings`() {
        val workflow = parseWorkflow(
            """
            version: 1
            name: Login
            # comments are fine
            steps:
              - call: login
                args:
                  port: "8080"
                  retries: 3
                  ratio: 0.5
                  remember: true
                  user: qa
                expect:
                  - path: $.ok
                    equals: true
            """.trimIndent(),
            source = "test.yaml",
        )

        val args = workflow.steps.single().args
        assertEquals(JsonPrimitive("8080"), args["port"])
        assertEquals(JsonPrimitive(3L), args["retries"])
        assertEquals(JsonPrimitive(0.5), args["ratio"])
        assertEquals(JsonPrimitive(true), args["remember"])
        assertEquals(JsonPrimitive("qa"), args["user"])
        assertEquals(JsonPrimitive(true), workflow.steps.single().expect.single().equals)
    }

    @Test
    fun `JSON is read as the YAML it is`() {
        val workflow = parseWorkflow("""{"version": 1, "name": "J", "steps": [{"call": "ping"}]}""", source = "test.json")

        assertEquals("ping", workflow.steps.single().call)
    }

    @Test
    fun `another format version is refused with the version this tool reads`() {
        val failure = assertFailsWith<WorkflowFormatException> { parseWorkflow("version: 2\nname: X\nsteps: []", source = "x.yaml") }

        assertTrue("version 1" in failure.message.orEmpty())
    }

    @Test
    fun `an unknown key is reported instead of ignored`() {
        assertFailsWith<WorkflowFormatException> {
            parseWorkflow("version: 1\nname: X\nsteps:\n  - call: a\n    expcet: []", source = "x.yaml")
        }
    }

    @Test
    fun `an exported workflow reads back to the same workflow`() {
        val workflow = parseWorkflow(
            """
            version: 1
            name: "Round: trip"
            inputs:
              email:
                default: qa@example.com
            steps:
              - id: create
                call: createAccount
                args:
                  email: ${'$'}{email}
                  tags: [a, "true", "12"]
                  nested:
                    empty: {}
                save:
                  id: $.id
              - call: getAccount
                args:
                  id: ${'$'}{id}
                expect:
                  - path: "$.items[?(@.text == 'Log in')].first()"
                    exists: true
            """.trimIndent(),
            source = "round.yaml",
        )

        assertEquals(workflow, parseWorkflow(workflowToYaml(workflow), source = "written.yaml"))
    }
}
