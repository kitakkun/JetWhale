package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.DefaultArgumentJson
import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpContent
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpTextCommand
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatchType
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class, ExperimentalSerializationApi::class)
class McpParameterDslTest {
    private fun execute(command: JetWhaleMcpCommand, vararg args: Pair<String, JsonElement>): String = runBlocking {
        command.execute(JetWhaleMcpArguments(JsonObject(args.toMap())))
            .content.filterIsInstance<JetWhaleMcpContent.Text>().joinToString(separator = "\n") { it.text }
    }

    private fun JetWhaleMcpCommand.schemaOf(parameter: String): JsonObject = toDescriptor().parameters.getValue(parameter).schema

    private fun JsonObject.obj(key: String): JsonObject = get(key) as JsonObject

    private fun JsonObject.property(name: String): JsonObject = obj("properties").obj(name)

    private fun JsonObject.strings(key: String): List<String> = (get(key) as JsonArray).map { (it as JsonPrimitive).content }

    private class StringMapCommand : JetWhaleMcpTextCommand() {
        override val name = "test.stringMap"
        override val description = "echoes a string map"
        val headers by stringMap("A string-to-string map.")
        override suspend fun executeText(arguments: JetWhaleMcpArguments): String = arguments[headers].entries.joinToString(",") { "${it.key}=${it.value}" }
    }

    private class OptionalStringMapCommand : JetWhaleMcpTextCommand() {
        override val name = "test.optionalStringMap"
        override val description = "echoes an optional string map"
        val headers by stringMapOrNull("An optional string-to-string map.")
        override suspend fun executeText(arguments: JetWhaleMcpArguments): String = arguments[headers]?.size?.toString() ?: "absent"
    }

    private class StringListCommand : JetWhaleMcpTextCommand() {
        override val name = "test.stringList"
        override val description = "echoes a string list"
        val items by stringList("A list of strings.")
        override suspend fun executeText(arguments: JetWhaleMcpArguments): String = arguments[items].joinToString(",")
    }

    private class JsonObjectCommand : JetWhaleMcpTextCommand() {
        override val name = "test.jsonObject"
        override val description = "echoes a raw json object"
        val payload by jsonObject("A raw JSON object.")
        override suspend fun executeText(arguments: JetWhaleMcpArguments): String = arguments[payload].toString()
    }

    private class JsonArrayCommand : JetWhaleMcpTextCommand() {
        override val name = "test.jsonArray"
        override val description = "echoes a raw json array"
        val payload by jsonArray("A raw JSON array.")
        override suspend fun executeText(arguments: JetWhaleMcpArguments): String = arguments[payload].size.toString()
    }

    private class EnumCommand : JetWhaleMcpTextCommand() {
        override val name = "test.enum"
        override val description = "echoes an enum"
        val matchType by enum("How the pattern is compared.", MockMatchType.entries)
        override suspend fun executeText(arguments: JetWhaleMcpArguments): String = arguments[matchType].name
    }

    private class SerializableCommand : JetWhaleMcpTextCommand() {
        override val name = "test.serializable"
        override val description = "echoes serializable mock rules"
        val rules by serializable<List<MockRule>>("The mock rules to apply.")
        override suspend fun executeText(arguments: JetWhaleMcpArguments): String = arguments[rules].joinToString(",") { "${it.id}:${it.matcher.matchType}" }
    }

    // Both the advertised schema and the decoder come from this format, so they cannot disagree.
    private class SnakeCaseCommand :
        JetWhaleMcpTextCommand(
            Json(from = DefaultArgumentJson) { namingStrategy = JsonNamingStrategy.SnakeCase },
        ) {
        override val name = "test.snakeCase"
        override val description = "echoes mock rules named in snake_case"
        val rules by serializable<List<MockRule>>("The mock rules to apply.")
        override suspend fun executeText(arguments: JetWhaleMcpArguments): String = arguments[rules].single().matcher.urlPattern
    }

    // PluginFrame is a sealed interface whose subclasses (including those of the nested sealed
    // Reply) kotlinx flattens into one set of leaves.
    private class SealedCommand : JetWhaleMcpTextCommand() {
        override val name = "test.sealed"
        override val description = "echoes a plugin frame"
        val frame by serializable<PluginFrame>("A plugin frame.")
        override suspend fun executeText(arguments: JetWhaleMcpArguments): String = arguments[frame].let { "${it::class.simpleName}:${it.pluginId}" }
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
    fun `the descriptor emits object and array schemas with element schemas`() {
        assertEquals(
            """{"type":"object","additionalProperties":{"type":"string"}}""",
            StringMapCommand().schemaOf("headers").toString(),
        )
        assertEquals(
            """{"type":"array","items":{"type":"string"}}""",
            StringListCommand().schemaOf("items").toString(),
        )
        assertEquals("""{"type":"object"}""", JsonObjectCommand().schemaOf("payload").toString())
        assertEquals("""{"type":"array"}""", JsonArrayCommand().schemaOf("payload").toString())
    }

    @Test
    fun `an enum parameter advertises its entry names`() {
        assertEquals(
            """{"type":"string","enum":["CONTAINS","EXACT","REGEX"]}""",
            EnumCommand().schemaOf("matchType").toString(),
        )
    }

    @Test
    fun `serializable decodes a nested value`() {
        val rule = MockRule(
            id = "rule-1",
            matcher = MockMatcher(urlPattern = "/api", matchType = MockMatchType.EXACT),
            response = MockResponseSpec(),
        )
        val result = execute(SerializableCommand(), "rules" to Json.encodeToJsonElement(listOf(rule)))
        assertEquals("rule-1:EXACT", result)
    }

    @Test
    fun `serializable tolerates unknown keys so a round-tripped value still decodes`() {
        val payload = buildJsonArray {
            add(
                buildJsonObject {
                    put("id", "rule-1")
                    put("annotatedByTheAgent", "ignore me")
                    put("matcher", buildJsonObject { put("urlPattern", "/api") })
                    put("response", buildJsonObject { })
                },
            )
        }
        assertEquals("rule-1:CONTAINS", execute(SerializableCommand(), "rules" to payload))
    }

    @Test
    fun `the derived schema advertises nested objects and enum entries`() {
        val schema = SerializableCommand().schemaOf("rules")
        assertEquals("array", (schema.getValue("type") as JsonPrimitive).content)

        val rule = schema.obj("items")
        assertEquals("object", (rule.getValue("type") as JsonPrimitive).content)
        assertEquals("string", (rule.property("id").getValue("type") as JsonPrimitive).content)

        val matchType = rule.property("matcher").property("matchType")
        assertEquals(listOf("CONTAINS", "EXACT", "REGEX"), matchType.strings("enum"))
    }

    @Test
    fun `the derived schema requires only properties without defaults`() {
        val rule = SerializableCommand().schemaOf("rules").obj("items")
        assertEquals(listOf("id", "matcher", "response"), rule.strings("required"))
        assertEquals(listOf("urlPattern"), rule.property("matcher").strings("required"))
        // Every property of MockResponseSpec has a default, so nothing is required.
        assertNull(rule.property("response")["required"])
    }

    @Test
    fun `the derived schema maps a Map property to additionalProperties`() {
        val headers = SerializableCommand().schemaOf("rules").obj("items").property("response").property("headers")
        assertEquals("object", (headers.getValue("type") as JsonPrimitive).content)
        assertEquals("string", (headers.obj("additionalProperties").getValue("type") as JsonPrimitive).content)
    }

    @Test
    fun `McpDescription surfaces as a property description in the derived schema`() {
        val rule = SerializableCommand().schemaOf("rules").obj("items")
        assertEquals(
            "Human-readable rule name shown in the UI.",
            (rule.property("name").getValue("description") as JsonPrimitive).content,
        )
        // A class-level annotation describes the nested object itself.
        assertEquals(
            "Which requests the rule applies to.",
            (rule.property("matcher").getValue("description") as JsonPrimitive).content,
        )
    }

    // The oneOf shape itself is covered by McpJsonSchemaTest; this checks that a value written in
    // that shape actually decodes back through the DSL.
    @Test
    fun `serializable decodes a sealed value through its class discriminator`() {
        val frame = PluginFrame.Notification(pluginId = "plugin-1", messageType = "m", payload = "{}")
        assertEquals(
            "Notification:plugin-1",
            execute(SealedCommand(), "frame" to Json.encodeToJsonElement(PluginFrame.serializer(), frame)),
        )
    }

    @Test
    fun `the default format accepts an enum entry written in the wrong case`() {
        val payload = buildJsonArray {
            add(
                buildJsonObject {
                    put("id", "rule-1")
                    put(
                        "matcher",
                        buildJsonObject {
                            put("urlPattern", "/api")
                            put("matchType", "exact")
                        },
                    )
                    put("response", buildJsonObject { })
                },
            )
        }
        assertEquals("rule-1:EXACT", execute(SerializableCommand(), "rules" to payload))
    }

    @Test
    fun `a custom format drives both the advertised names and the decoding`() {
        val command = SnakeCaseCommand()
        val matcher = command.schemaOf("rules").obj("items").property("matcher")
        assertEquals(listOf("method", "url_pattern", "match_type"), matcher.obj("properties").keys.toList())

        val payload = buildJsonArray {
            add(
                buildJsonObject {
                    put("id", "rule-1")
                    put("matcher", buildJsonObject { put("url_pattern", "/api") })
                    put("response", buildJsonObject { })
                },
            )
        }
        assertEquals("/api", execute(command, "rules" to payload))
    }

    @Test
    fun `a payload that does not fit the serializable type throws a caller-facing exception`() {
        val exception = assertFailsWith<JetWhaleMcpArgumentException> {
            execute(SerializableCommand(), "rules" to buildJsonArray { add(buildJsonObject { put("id", "rule-1") }) })
        }
        assertTrue("invalid rules" in exception.message!!, exception.message!!)
    }

    // -- Output declaration ---------------------------------------------------------------------

    private class OutputCommand : JetWhaleMcpCommand() {
        override val name = "test.output"
        override val description = "answers with a mock configuration"
        private val mockConfig = serializableOutput<MockConfigResult>()
        override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = mockConfig.result(
            MockConfigResult(enabled = true, rules = listOf(MockRule(id = "r1", matcher = MockMatcher(urlPattern = "/api"), response = MockResponseSpec()))),
        )
    }

    // The output schema follows the command's format for the same reason a parameter's does.
    private class SnakeCaseOutputCommand : JetWhaleMcpCommand(Json(from = DefaultArgumentJson) { namingStrategy = JsonNamingStrategy.SnakeCase }) {
        override val name = "test.snakeCaseOutput"
        override val description = "answers with a snake_case mock configuration"
        private val mockConfig = serializableOutput<MockConfigResult>()
        override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = mockConfig.result(MockConfigResult(enabled = false, rules = emptyList()))
    }

    @Test
    fun `the derived output schema advertises the properties and the ones without defaults`() {
        val schema = assertNotNull(OutputCommand().toDescriptor().outputSchema)
        assertEquals("object", (schema.getValue("type") as JsonPrimitive).content)
        assertEquals(listOf("enabled", "rules"), schema.obj("properties").keys.toList())
        assertEquals(listOf("enabled", "rules"), schema.strings("required"))

        val rule = schema.property("rules").obj("items")
        assertEquals(listOf("id", "matcher", "response"), rule.strings("required"))
        assertEquals(
            "Whether response mocking is enabled globally on the debuggee.",
            (schema.property("enabled").getValue("description") as JsonPrimitive).content,
        )
    }

    @Test
    fun `a declared output answers with structured content matching its schema`() {
        val result = runBlocking { OutputCommand().execute(JetWhaleMcpArguments(JsonObject(emptyMap()))) }
        val payload = assertNotNull(result.structuredContent)
        assertEquals(setOf("enabled", "rules"), payload.keys)
        assertTrue(payload.getValue("enabled") is JsonPrimitive)
        // The same payload is repeated as text, which is what JetWhaleMcpResult.json produces.
        assertEquals(payload.toString(), execute(OutputCommand()))
    }

    @Test
    fun `a command that declares no output advertises none`() {
        assertNull(StringMapCommand().toDescriptor().outputSchema)
    }

    @Test
    fun `a custom format drives both the advertised output names and the encoding`() {
        val command = SnakeCaseOutputCommand()
        val rule = assertNotNull(command.toDescriptor().outputSchema).property("rules").obj("items")
        assertEquals(listOf("method", "url_pattern", "match_type"), rule.property("matcher").obj("properties").keys.toList())
    }

    @Test
    fun `an output type that is not a JSON object fails fast`() {
        val exception = assertFailsWith<IllegalStateException> {
            object : JetWhaleMcpCommand() {
                override val name = "test.listOutput"
                override val description = "tries to answer with a bare list"
                private val rules = serializableOutput<List<MockRule>>()
                override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = rules.result(emptyList())
            }
        }
        assertTrue("does not serialize to a JSON object" in exception.message!!, exception.message!!)
    }

    @Test
    fun `declaring a second output fails fast`() {
        val exception = assertFailsWith<IllegalStateException> {
            object : JetWhaleMcpCommand() {
                override val name = "test.twoOutputs"
                override val description = "declares two outputs"
                val config = serializableOutput<MockConfigResult>()
                val rules = serializableOutput<MockRulesResult>()
                override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = config.result(MockConfigResult(enabled = true, rules = emptyList()))
            }
        }
        assertTrue("more than one output" in exception.message!!, exception.message!!)
    }

    @Test
    fun `declaring an output after the schema was read fails fast`() {
        val command = object : JetWhaleMcpCommand() {
            override val name = "test.lateOutput"
            override val description = "declares its output inside execute"
            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = serializableOutput<MockRulesResult>().result(MockRulesResult(rules = emptyList()))
        }
        command.toDescriptor()
        val exception = assertFailsWith<IllegalStateException> { execute(command) }
        assertTrue("after its schema was read" in exception.message!!, exception.message!!)
    }
}
