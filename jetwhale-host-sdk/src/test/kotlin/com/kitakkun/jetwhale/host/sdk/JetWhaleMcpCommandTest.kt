@file:OptIn(ExperimentalJetWhaleApi::class, ExperimentalSerializationApi::class)

package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.annotations.McpDescription
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
}
