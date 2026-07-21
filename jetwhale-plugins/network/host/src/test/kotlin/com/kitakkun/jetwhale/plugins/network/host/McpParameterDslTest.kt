package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class)
class McpParameterDslTest {
    private fun execute(command: JetWhaleMcpCommand, vararg args: Pair<String, JsonElement>): String = runBlocking { command.execute(JetWhaleMcpArguments(JsonObject(args.toMap()))) }

    private class StringMapCommand : JetWhaleMcpCommand() {
        override val name = "test.stringMap"
        override val description = "echoes a string map"
        val headers by stringMap("A string-to-string map.")
        override suspend fun execute(arguments: JetWhaleMcpArguments): String = arguments[headers].entries.joinToString(",") { "${it.key}=${it.value}" }
    }

    private class OptionalStringMapCommand : JetWhaleMcpCommand() {
        override val name = "test.optionalStringMap"
        override val description = "echoes an optional string map"
        val headers by stringMapOrNull("An optional string-to-string map.")
        override suspend fun execute(arguments: JetWhaleMcpArguments): String = arguments[headers]?.size?.toString() ?: "absent"
    }

    private class StringListCommand : JetWhaleMcpCommand() {
        override val name = "test.stringList"
        override val description = "echoes a string list"
        val items by stringList("A list of strings.")
        override suspend fun execute(arguments: JetWhaleMcpArguments): String = arguments[items].joinToString(",")
    }

    private class JsonObjectCommand : JetWhaleMcpCommand() {
        override val name = "test.jsonObject"
        override val description = "echoes a raw json object"
        val payload by jsonObject("A raw JSON object.")
        override suspend fun execute(arguments: JetWhaleMcpArguments): String = arguments[payload].toString()
    }

    private class JsonArrayCommand : JetWhaleMcpCommand() {
        override val name = "test.jsonArray"
        override val description = "echoes a raw json array"
        val payload by jsonArray("A raw JSON array.")
        override suspend fun execute(arguments: JetWhaleMcpArguments): String = arguments[payload].size.toString()
    }

    @Test
    fun `stringMap parses a JSON object into a string map`() {
        val result = execute(
            StringMapCommand(),
            "headers" to buildJsonObject {
                put("Content-Type", "application/json")
                put("X-Trace", "abc")
            },
        )
        assertEquals("Content-Type=application/json,X-Trace=abc", result)
    }

    @Test
    fun `optional stringMap is null when omitted`() {
        assertEquals("absent", execute(OptionalStringMapCommand()))
    }

    @Test
    fun `stringList parses a JSON array into a list of strings`() {
        val result = execute(
            StringListCommand(),
            "items" to buildJsonArray {
                add(JsonPrimitive("a"))
                add(JsonPrimitive("b"))
            },
        )
        assertEquals("a,b", result)
    }

    @Test
    fun `jsonObject and jsonArray hand back the raw element`() {
        assertEquals(
            """{"k":"v"}""",
            execute(JsonObjectCommand(), "payload" to buildJsonObject { put("k", "v") }),
        )
        assertEquals(
            "2",
            execute(
                JsonArrayCommand(),
                "payload" to buildJsonArray {
                    add(JsonPrimitive("x"))
                    add(JsonPrimitive("y"))
                },
            ),
        )
    }

    @Test
    fun `a scalar passed where an object is expected throws a caller-facing exception`() {
        val exception = assertFailsWith<JetWhaleMcpArgumentException> {
            execute(StringMapCommand(), "headers" to JsonPrimitive("not-an-object"))
        }
        assertTrue("headers" in exception.message!!, exception.message!!)
    }

    @Test
    fun `a missing required structured argument throws a caller-facing exception`() {
        val exception = assertFailsWith<JetWhaleMcpArgumentException> {
            execute(StringMapCommand())
        }
        assertTrue("missing required argument: headers" in exception.message!!, exception.message!!)
    }

    @Test
    fun `the descriptor emits object and array types with element schemas`() {
        val mapDescriptor = StringMapCommand().toDescriptor().parameters.getValue("headers")
        assertEquals("object", mapDescriptor.type)
        assertEquals("string", mapDescriptor.valueType)
        assertNull(mapDescriptor.itemsType)

        val listDescriptor = StringListCommand().toDescriptor().parameters.getValue("items")
        assertEquals("array", listDescriptor.type)
        assertEquals("string", listDescriptor.itemsType)
        assertNull(listDescriptor.valueType)

        val rawObject = JsonObjectCommand().toDescriptor().parameters.getValue("payload")
        assertEquals("object", rawObject.type)
        assertNull(rawObject.valueType)

        val rawArray = JsonArrayCommand().toDescriptor().parameters.getValue("payload")
        assertEquals("array", rawArray.type)
        assertNull(rawArray.itemsType)
    }
}
