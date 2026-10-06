@file:OptIn(ExperimentalJetWhaleApi::class, ExperimentalSerializationApi::class)

package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.annotations.McpDescription
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class Measurement(
    @McpDescription("Width in pixels.")
    val widthPx: Int,
    val heightPx: Int,
    val label: String = "",
)

@Serializable
private data class Answer(val detail: String?)

private val noArguments = JetWhaleMcpArguments(JsonObject(emptyMap()))

private class MeasureCommand(json: Json) : JetWhaleMcpCommand(json) {
    override val name = "test.measure"
    override val description = "answers with a measurement"
    private val measurement = serializableOutput<Measurement>()
    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = measurement.result(Measurement(widthPx = 120, heightPx = 40))
}

private class AnswerCommand(json: Json) : JetWhaleMcpCommand(json) {
    override val name = "test.answer"
    override val description = "answers with a possibly null detail"
    private val answer = serializableOutput<Answer>()
    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = answer.result(Answer(detail = null))
}

private object ArrayAnswerSerializer : KSerializer<Answer> {
    private val wire = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ArrayAnswer") { element<String>("detail") }
    override fun serialize(encoder: Encoder, value: Answer) = encoder.encodeSerializableValue(wire, listOf(value.detail.orEmpty()))
    override fun deserialize(decoder: Decoder): Answer = Answer(decoder.decodeSerializableValue(wire).single())
}

@Serializable
private data class Inner(val value: String)

@Serializable
private data class Outer(val inner: Inner, val note: String?)

private class Stamp(val epochSeconds: Long)

private object StampAsStringSerializer : KSerializer<Stamp> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("test.Stamp", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Stamp) = encoder.encodeString(value.epochSeconds.toString())
    override fun deserialize(decoder: Decoder): Stamp = Stamp(decoder.decodeString().toLong())
}

@Serializable
private sealed interface Outcome {
    @Serializable
    data class Ok(val value: Int) : Outcome

    @Serializable
    data class Partial(val value: Int, val note: String? = null) : Outcome
}

@Serializable
private data class Report(
    @Contextual val at: Stamp,
    val payload: JsonObject,
    val anything: JsonElement,
    val scalar: JsonPrimitive,
    val nothing: JsonNull,
    val inner: Inner?,
    val outcome: Outcome,
)

private class ReportCommand(json: Json) : JetWhaleMcpCommand(json) {
    override val name = "test.report"
    override val description = "answers with a report"
    private val report = serializableOutput<Report>()
    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = report.result(
        Report(
            at = Stamp(epochSeconds = 5),
            payload = buildJsonObject { put("count", 1) },
            anything = buildJsonArray {
                add(1)
                add("two")
            },
            scalar = JsonPrimitive(3),
            nothing = JsonNull,
            inner = Inner(value = "x"),
            outcome = Outcome.Ok(value = 1),
        ),
    )
}

@JvmInline
@Serializable
private value class Code(val value: String)

private enum class Phase { Started }

private interface Tag

@Serializable
private data class NamedTag(val name: String) : Tag

@Serializable
private data class Ledger(
    val outcome: Outcome,
    val tag: Tag,
    val byInner: Map<Inner, Int>,
    val byCode: Map<Code, Int>,
    val byStamp: Map<@Contextual Stamp, Int>,
    val byPhase: Map<Phase, Int>,
)

private class LedgerCommand(json: Json) : JetWhaleMcpCommand(json) {
    override val name = "test.ledger"
    override val description = "answers with a ledger"
    private val ledger = serializableOutput<Ledger>()
    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = ledger.result(
        Ledger(
            outcome = Outcome.Ok(value = 1),
            tag = NamedTag(name = "a"),
            byInner = mapOf(Inner(value = "x") to 1),
            byCode = mapOf(Code("c") to 2),
            byStamp = mapOf(Stamp(epochSeconds = 5) to 3),
            byPhase = mapOf(Phase.Started to 4),
        ),
    )
}

class JetWhaleMcpCommandTest {
    @Test
    fun `a declared output advertises its properties and the ones without a default as required`() {
        val schema = assertNotNull(MeasureCommand(DefaultArgumentJson).toDescriptor().outputSchema)

        assertEquals("object", (schema.getValue("type") as JsonPrimitive).content)
        assertEquals(listOf("widthPx", "heightPx", "label"), (schema.getValue("properties") as JsonObject).keys.toList())
        assertEquals(listOf("widthPx", "heightPx"), (schema.getValue("required") as JsonArray).map { (it as JsonPrimitive).content })
    }

    @Test
    fun `a command that declares no output advertises none`() {
        val command = object : JetWhaleMcpTextCommand() {
            override val name = "test.text"
            override val description = "answers with text"
            override suspend fun executeText(arguments: JetWhaleMcpArguments): String = "ok"
        }

        assertNull(command.toDescriptor().outputSchema)
    }

    @Test
    fun `a declared output answers with structured content repeated as text`() {
        val result = runBlocking { MeasureCommand(DefaultArgumentJson).run(noArguments) }

        val expected = buildJsonObject {
            put("widthPx", 120)
            put("heightPx", 40)
        }
        assertEquals(expected, result.structuredContent)
        assertEquals(expected.toString(), (result.content.single() as JetWhaleMcpContent.Text).text)
    }

    @Test
    fun `the command's format names the output's properties`() {
        val snakeCase = Json(from = DefaultArgumentJson) { namingStrategy = JsonNamingStrategy.SnakeCase }
        val command = MeasureCommand(snakeCase)

        assertEquals(listOf("width_px", "height_px", "label"), (assertNotNull(command.toDescriptor().outputSchema).getValue("properties") as JsonObject).keys.toList())
        assertEquals(setOf("width_px", "height_px"), assertNotNull(runBlocking { command.run(noArguments) }.structuredContent).keys)
    }

    @Test
    fun `a format without explicit nulls neither requires nor writes a null property`() {
        val command = AnswerCommand(Json(from = DefaultArgumentJson) { explicitNulls = false })

        assertNull(assertNotNull(command.toDescriptor().outputSchema)["required"])
        assertEquals(emptySet(), assertNotNull(runBlocking { command.run(noArguments) }.structuredContent).keys)
    }

    @Test
    fun `a successful answer built around a declared output is refused`() {
        val command = object : JetWhaleMcpCommand() {
            override val name = "test.bypassedOutput"
            override val description = "declares an output and then answers around it"
            private val measurement = serializableOutput<Measurement>()
            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = JetWhaleMcpResult.json(buildJsonObject { put("widthPx", 1) })
        }

        val exception = assertFailsWith<IllegalStateException> { runBlocking { command.run(noArguments) } }
        assertContains(exception.message.orEmpty(), "declares an output but answered without it")
    }

    @Test
    fun `a declared output still lets the command report a failure`() {
        val command = object : JetWhaleMcpCommand() {
            override val name = "test.failingOutput"
            override val description = "declares an output and fails"
            private val measurement = serializableOutput<Measurement>()
            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = JetWhaleMcpResult.error("the widget is gone")
        }

        assertTrue(runBlocking { command.run(noArguments) }.isError)
    }

    @Test
    fun `an image added to a declared output's result keeps it acceptable`() {
        val command = object : JetWhaleMcpCommand() {
            override val name = "test.outputWithImage"
            override val description = "answers with a measurement and a picture of it"
            private val measurement = serializableOutput<Measurement>()
            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = measurement.result(Measurement(widthPx = 1, heightPx = 1)).withImage(byteArrayOf(1, 2), "image/png")
        }

        val result = runBlocking { command.run(noArguments) }

        assertEquals(listOf("image/png"), result.content.filterIsInstance<JetWhaleMcpContent.Image>().map(JetWhaleMcpContent.Image::mimeType))
    }

    @Test
    fun `an output type that is a list fails fast`() {
        val exception = assertFailsWith<IllegalStateException> {
            object : JetWhaleMcpCommand() {
                override val name = "test.listOutput"
                override val description = "tries to answer with a bare list"
                private val measurements = serializableOutput<List<Measurement>>()
                override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = measurements.result(emptyList())
            }
        }
        assertContains(exception.message.orEmpty(), "does not serialize to a JSON object with named properties")
    }

    @Test
    fun `an output type that is a map fails fast`() {
        val exception = assertFailsWith<IllegalStateException> {
            object : JetWhaleMcpCommand() {
                override val name = "test.mapOutput"
                override val description = "tries to answer with a bare map"
                private val counts = serializableOutput<Map<String, Int>>()
                override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = counts.result(emptyMap())
            }
        }
        assertContains(exception.message.orEmpty(), "named properties")
    }

    @Test
    fun `a text command cannot declare an output`() {
        val exception = assertFailsWith<IllegalStateException> {
            object : JetWhaleMcpTextCommand() {
                override val name = "test.textWithOutput"
                override val description = "a text command that tries to promise a shape"
                private val measurement = serializableOutput<Measurement>()
                override suspend fun executeText(arguments: JetWhaleMcpArguments): String = "ok"
            }
        }
        assertContains(exception.message.orEmpty(), "cannot declare an output")
    }

    @Test
    fun `declaring a second output fails fast`() {
        val exception = assertFailsWith<IllegalStateException> {
            object : JetWhaleMcpCommand() {
                override val name = "test.twoOutputs"
                override val description = "declares two outputs"
                private val measurement = serializableOutput<Measurement>()
                private val answer = serializableOutput<Answer>()
                override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = answer.result(Answer(detail = "d"))
            }
        }
        assertContains(exception.message.orEmpty(), "more than one output")
    }

    @Test
    fun `declaring an output after the schema was read fails fast`() {
        val command = object : JetWhaleMcpCommand() {
            override val name = "test.lateOutput"
            override val description = "declares its output inside execute"
            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = serializableOutput<Answer>().result(Answer(detail = "d"))
        }
        command.toDescriptor()

        val exception = assertFailsWith<IllegalStateException> { runBlocking { command.run(noArguments) } }
        assertContains(exception.message.orEmpty(), "after its schema was read")
    }

    @Test
    fun `an output whose serializer writes something other than an object is refused when it runs`() {
        val command = object : JetWhaleMcpCommand() {
            override val name = "test.arrayOutput"
            override val description = "declares an object and encodes an array"
            private val answer = serializableOutput(ArrayAnswerSerializer)
            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = answer.result(Answer(detail = "d"))
        }

        val exception = assertFailsWith<IllegalStateException> { runBlocking { command.run(noArguments) } }
        assertContains(exception.message.orEmpty(), "does not fit the output schema")
    }

    @Test
    fun `a text command answers with its text as a success`() {
        val command = object : JetWhaleMcpTextCommand() {
            override val name = "test.echo"
            override val description = "echoes"
            override suspend fun executeText(arguments: JetWhaleMcpArguments): String = "hello"
        }

        assertEquals(JetWhaleMcpResult.text("hello"), runBlocking { command.run(noArguments) })
    }

    @Test
    fun `an argument that follows the advertised schema decodes under a format that writes a discriminator on every object`() {
        val json = Json { classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS }
        val command = object : JetWhaleMcpCommand(json) {
            override val name = "test.readOuter"
            override val description = "reads an Outer"
            private val outer by serializable<Outer>("The value to read.")
            private val fallback by serializableOrNull<Outer>("The value to read after outer.")
            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = JetWhaleMcpResult.text(arguments[outer].inner.value + arguments[fallback]?.inner?.value)
        }
        val argument = buildJsonObject {
            putJsonObject("inner") { put("value", "x") }
            put("note", JsonNull)
        }
        val parameters = command.toDescriptor().parameters

        assertConforms(argument, parameters.getValue("outer").schema)
        assertConforms(argument, parameters.getValue("fallback").schema)
        val arguments = buildJsonObject {
            put("outer", argument)
            put("fallback", argument)
        }
        assertEquals(JetWhaleMcpResult.text("xx"), runBlocking { command.run(JetWhaleMcpArguments(arguments)) })
    }

    @Test
    fun `a declared output pins the discriminator a format writes on every object`() {
        val command = MeasureCommand(Json(from = DefaultArgumentJson) { classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS })
        val schema = assertNotNull(command.toDescriptor().outputSchema)
        val written = assertNotNull(runBlocking { command.run(noArguments) }.structuredContent)

        assertEquals(written.getValue("type"), (schema.getValue("properties") as JsonObject).getValue("type").jsonObject.getValue("const"))
        assertEquals("type", (schema.getValue("required") as JsonArray).first().jsonPrimitive.content)
    }

    @Test
    fun `a declared output's answer conforms to the schema it advertises`() {
        val module = SerializersModule { contextual(Stamp::class, StampAsStringSerializer) }
        val formats = listOf(
            Json(from = DefaultArgumentJson) { serializersModule = module },
            Json(from = DefaultArgumentJson) {
                serializersModule = module
                classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS
            },
            Json(from = DefaultArgumentJson) {
                serializersModule = module
                classDiscriminatorMode = ClassDiscriminatorMode.NONE
            },
        )

        for (json in formats) {
            val command = ReportCommand(json)
            val schema = assertNotNull(command.toDescriptor().outputSchema)
            assertConforms(assertNotNull(runBlocking { command.run(noArguments) }.structuredContent), schema)
        }
    }

    @Test
    fun `a declared output's answer conforms to the schema it advertises under a format that writes polymorphic values and structured map keys as arrays`() {
        val json = Json(from = DefaultArgumentJson) {
            useArrayPolymorphism = true
            allowStructuredMapKeys = true
            serializersModule = SerializersModule {
                contextual(Stamp::class, StampAsStringSerializer)
                polymorphic(Tag::class) { subclass(NamedTag::class) }
            }
        }
        val command = LedgerCommand(json)
        val schema = assertNotNull(command.toDescriptor().outputSchema)

        assertConforms(assertNotNull(runBlocking { command.run(noArguments) }.structuredContent), schema)
    }

    private fun assertConforms(value: JsonElement, schema: JsonObject) {
        val violations = violations(value, schema, path = "$")
        assertTrue(violations.isEmpty(), "$value does not conform to $schema: $violations")
    }

    /**
     * Checks [value] against the part of JSON Schema the walker emits. A keyword outside that part
     * is reported rather than skipped, so a schema passes only when every constraint in it was checked.
     */
    private fun violations(value: JsonElement, schema: JsonObject, path: String): List<String> {
        val unknownKeywords = schema.keys - setOf("type", "properties", "required", "additionalProperties", "items", "enum", "const", "oneOf", "anyOf", "description")
        if (unknownKeywords.isNotEmpty()) return listOf("$path: unchecked keywords $unknownKeywords")
        val problems = mutableListOf<String>()
        schema["type"]?.let { type ->
            val allowed = (type as? JsonArray)?.map { it.jsonPrimitive.content } ?: listOf(type.jsonPrimitive.content)
            if (allowed.none { value.hasJsonSchemaType(it) }) problems += "$path: $value is none of $allowed"
        }
        schema["enum"]?.let { if (value !in it.jsonArray) problems += "$path: $value is not in $it" }
        schema["const"]?.let { if (value != it) problems += "$path: $value is not $it" }
        schema["oneOf"]?.let { variants ->
            val matching = variants.jsonArray.count { violations(value, it.jsonObject, path).isEmpty() }
            if (matching != 1) problems += "$path: $value matches $matching oneOf variants instead of one"
        }
        schema["anyOf"]?.let { variants ->
            if (variants.jsonArray.none { violations(value, it.jsonObject, path).isEmpty() }) problems += "$path: $value matches no anyOf variant"
        }
        if (value is JsonObject) {
            schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }?.filterNot(value::containsKey)?.forEach { problems += "$path: $it is missing" }
            for ((key, item) in value) {
                val itemSchema = schema["properties"]?.jsonObject?.get(key) ?: schema["additionalProperties"] ?: continue
                problems += violations(item, itemSchema.jsonObject, "$path.$key")
            }
        }
        if (value is JsonArray) {
            schema["items"]?.let { items -> value.forEachIndexed { index, item -> problems += violations(item, items.jsonObject, "$path[$index]") } }
        }
        return problems
    }

    private fun JsonElement.hasJsonSchemaType(type: String): Boolean = when (type) {
        "object" -> this is JsonObject
        "array" -> this is JsonArray
        "null" -> this is JsonNull
        "string" -> this is JsonPrimitive && isString
        "boolean" -> this is JsonPrimitive && !isString && booleanOrNull != null
        "integer" -> this is JsonPrimitive && !isString && longOrNull != null
        "number" -> this is JsonPrimitive && !isString && doubleOrNull != null
        else -> error("unknown JSON Schema type $type")
    }
}
