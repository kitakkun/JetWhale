package com.kitakkun.jetwhale.tools.mcpworkflow

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JsonPathTest {
    private val document = Json.parseToJsonElement(
        """{"stacks":[{"entries":[{"typeName":"Home"},{"typeName":"Detail"}]}],"nodes":[{"id":7,"text":"Settings"},{"id":9,"text":"Log in"}],"odd key":1}""",
    )

    @Test
    fun `members and indices select one value`() {
        assertEquals(JsonPrimitive("Home"), select("$.stacks[0].entries[0].typeName"))
        assertEquals(JsonPrimitive(1), select("$['odd key']"))
    }

    @Test
    fun `a negative index counts from the end`() {
        assertEquals(JsonPrimitive("Detail"), select("$.stacks[0].entries[-1].typeName"))
    }

    @Test
    fun `a wildcard selects every item and applies later segments to each`() {
        assertEquals(JsonArray(listOf(JsonPrimitive(7), JsonPrimitive(9))), select("$.nodes[*].id"))
    }

    @Test
    fun `a filter with first picks one item by a field`() {
        assertEquals(JsonPrimitive(9), select("$.nodes[?(@.text == 'Log in')].first().id"))
        assertEquals(JsonPrimitive(7), select("$.nodes[?(@.text != 'Log in')].first().id"))
    }

    @Test
    fun `a filter can require several fields at once`() {
        val sessions = Json.parseToJsonElement("""[{"name":"Mac","isActive":false,"id":1},{"name":"Mac","isActive":true,"id":2}]""")

        assertEquals(JsonPrimitive(2), JsonPath.parse("$[?(@.name == 'Mac' && @.isActive == true)].first().id").select(sessions))
    }

    @Test
    fun `a filter without first selects a list which may be empty`() {
        assertEquals(JsonArray(emptyList()), select("$.nodes[?(@.text == 'Nope')]"))
    }

    @Test
    fun `a missing member selects nothing`() {
        assertNull(select("$.stacks[3].entries"))
    }

    @Test
    fun `a path outside the subset is rejected`() {
        assertFailsWith<IllegalArgumentException> { JsonPath.parse("stacks.0") }
        assertFailsWith<IllegalArgumentException> { JsonPath.parse("$.nodes[?(@.id > 3)]") }
    }

    private fun select(path: String) = JsonPath.parse(path).select(document)
}
