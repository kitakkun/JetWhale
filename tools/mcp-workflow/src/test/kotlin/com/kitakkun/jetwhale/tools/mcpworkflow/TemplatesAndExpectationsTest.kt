package com.kitakkun.jetwhale.tools.mcpworkflow

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TemplatesAndExpectationsTest {
    private val variables = mapOf(
        "id" to JsonPrimitive(42),
        "node" to Json.parseToJsonElement("""{"bounds":{"left":10}}"""),
        "name" to JsonPrimitive("qa"),
    )

    @Test
    fun `a string that is one placeholder keeps the variable's type`() {
        assertEquals(JsonPrimitive(42), renderTemplate(JsonPrimitive("\${id}"), variables, emptyMap()))
    }

    @Test
    fun `a placeholder inside text is spliced in as text`() {
        assertEquals(JsonPrimitive("user-qa-42"), renderTemplate(JsonPrimitive("user-\${name}-\${id}"), variables, emptyMap()))
    }

    @Test
    fun `a placeholder can read into a saved object and the environment`() {
        assertEquals(JsonPrimitive(10), renderTemplate(JsonPrimitive("\${node.bounds.left}"), variables, emptyMap()))
        assertEquals(JsonPrimitive("secret"), renderTemplate(JsonPrimitive("\${env.TOKEN}"), variables, mapOf("TOKEN" to "secret")))
    }

    @Test
    fun `an undefined variable is an error naming it`() {
        val failure = assertFailsWith<TemplateException> { renderTemplate(JsonPrimitive("\${missing}"), variables, emptyMap()) }

        assertEquals(true, "missing" in failure.message.orEmpty())
    }

    private val result = Json.parseToJsonElement("""{"size":2,"entries":["Home","Detail"],"title":"Settings page"}""")

    @Test
    fun `expectations hold for matching values and explain mismatches`() {
        assertNull(check(Expectation(path = "$.size", equals = JsonPrimitive(2.0)), isError = false))
        assertNull(check(Expectation(path = "$.entries", contains = JsonPrimitive("Detail"), length = 2), isError = false))
        assertNull(check(Expectation(path = "$.title", matches = "^Settings"), isError = false))
        assertNull(check(Expectation(path = "$.size", gte = 2.0, lt = 3.0), isError = false))
        assertNull(check(Expectation(path = "$.missing", exists = false), isError = false))

        val failure = assertNotNull(check(Expectation(path = "$.size", equals = JsonPrimitive(3)), isError = false))
        assertEquals("expected $.size to equal 3, but it was 2", failure)
    }

    @Test
    fun `an expectation about errors checks the tool's error flag`() {
        assertNull(check(Expectation(error = true), isError = true))
        assertNotNull(check(Expectation(error = false), isError = true))
    }

    @Test
    fun `validation finds unknown servers and variables used before they are saved`() {
        val workflow = Workflow(
            version = WORKFLOW_FORMAT_VERSION,
            name = "Broken",
            steps = listOf(
                Step(call = "a", server = "nope"),
                Step(call = "b", server = "app", args = buildJsonObject { put("id", "\${id}") }, save = mapOf("id" to "$.id")),
                Step(call = "c", server = "app", timeout = "soon"),
            ),
        )

        val problems = validate(workflow, servers = setOf("app"))

        assertEquals(3, problems.size, problems.joinToString("\n"))
    }

    private fun check(expectation: Expectation, isError: Boolean) = failureOf(expectation, result, isError)
}
