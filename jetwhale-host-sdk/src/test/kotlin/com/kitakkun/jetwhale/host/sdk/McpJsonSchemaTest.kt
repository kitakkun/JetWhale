@file:OptIn(ExperimentalSerializationApi::class)

package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.annotations.McpDescription
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Unit tests for the SerialDescriptor -> JSON Schema rules, over purpose-built types. The schema
// the Network Inspector actually advertises is guarded separately by McpParameterDslTest.
@Serializable
private data class Primitives(val text: String, val letter: Char, val count: Int, val size: Long, val ratio: Double, val flag: Boolean)

@Serializable
private data class Optionality(val requiredHere: String?, val optionalHere: String? = null)

/** A sealed type that pins no discriminator of its own, so the format's is used. */
@Serializable
private sealed interface Payload {
    @Serializable
    @SerialName("text")
    data class Text(val body: String) : Payload
}

@Serializable
private data class Node(val name: String, val children: List<Node> = emptyList())

@Serializable
@JvmInline
private value class RuleId(val value: String)

@Serializable
private data class HasValueClass(val id: RuleId)

@Serializable
private data class Nested(val counts: Map<String, Int>, val rows: List<List<String>>)

@Serializable
@JsonClassDiscriminator("kind")
private sealed interface Shape {
    @Serializable
    @SerialName("circle")
    @McpDescription("A circle.")
    data class Circle(val radius: Double) : Shape

    @Serializable
    @SerialName("nothing")
    data object Nothing : Shape
}

@Serializable
private abstract class OpenBase

class McpJsonSchemaTest {
    private inline fun <reified T> schemaOf(json: Json = DefaultArgumentJson): JsonObject = serializer<T>().descriptor.toJsonSchema(json)

    private fun JsonObject.obj(key: String): JsonObject = get(key) as JsonObject

    private fun JsonObject.property(name: String): JsonObject = obj("properties").obj(name)

    private fun JsonObject.string(key: String): String = (getValue(key) as JsonPrimitive).content

    private fun JsonObject.strings(key: String): List<String> = (get(key) as JsonArray).map { (it as JsonPrimitive).content }

    private fun JsonObject.variants(): List<JsonObject> = (getValue("oneOf") as JsonArray).map { it as JsonObject }

    @Test
    fun `primitive kinds map onto the JSON Schema types`() {
        val schema = schemaOf<Primitives>()
        assertEquals("string", schema.property("text").string("type"))
        assertEquals("string", schema.property("letter").string("type"))
        assertEquals("integer", schema.property("count").string("type"))
        assertEquals("integer", schema.property("size").string("type"))
        assertEquals("number", schema.property("ratio").string("type"))
        assertEquals("boolean", schema.property("flag").string("type"))
    }

    @Test
    fun `a nullable property without a default is still required`() {
        assertEquals(listOf("requiredHere"), schemaOf<Optionality>().strings("required"))
    }

    @Test
    fun `maps and nested lists keep their element schemas`() {
        val schema = schemaOf<Nested>()
        assertEquals("integer", schema.property("counts").obj("additionalProperties").string("type"))
        // The recursion guard covers classes only, so List<List<String>> is not cut short.
        assertEquals("string", schema.property("rows").obj("items").obj("items").string("type"))
    }

    @Test
    fun `a value class is transparent, showing its underlying type`() {
        assertEquals("string", schemaOf<HasValueClass>().property("id").string("type"))
    }

    @Test
    fun `a self-referencing type stops recursing at the second visit`() {
        val child = schemaOf<Node>().property("children").obj("items")
        assertEquals("object", child.string("type"))
        assertNull(child["properties"])
    }

    @Test
    fun `a JsonClassDiscriminator overrides the discriminator key of every variant`() {
        val variants = schemaOf<Shape>().variants()
        assertEquals(listOf("circle", "nothing"), variants.map { it.property("kind").string("const") })
        variants.forEach { assertEquals("kind", it.strings("required").first()) }
    }

    @Test
    fun `a sealed variant with no properties still requires the discriminator`() {
        val nothing = schemaOf<Shape>().variants().single { it.property("kind").string("const") == "nothing" }
        assertEquals(listOf("kind"), nothing.strings("required"))
        assertEquals(listOf("kind"), nothing.obj("properties").keys.toList())
    }

    @Test
    fun `a class-level McpDescription describes its sealed variant`() {
        val circle = schemaOf<Shape>().variants().single { it.property("kind").string("const") == "circle" }
        assertEquals("A circle.", circle.string("description"))
    }

    @Test
    fun `an open polymorphic type is advertised as an unconstrained object`() {
        val schema = PolymorphicSerializer(OpenBase::class).descriptor.toJsonSchema(DefaultArgumentJson)
        assertEquals("object", schema.string("type"))
        assertNull(schema["oneOf"])
    }

    @Test
    fun `the format's class discriminator is used when the type does not pin one`() {
        val json = Json(from = DefaultArgumentJson) { classDiscriminator = "@case" }
        val variants = schemaOf<Payload>(json).variants()
        assertEquals(listOf("text"), variants.map { it.property("@case").string("const") })
    }

    @Test
    fun `a type-level JsonClassDiscriminator still wins over the format's`() {
        val json = Json(from = DefaultArgumentJson) { classDiscriminator = "@case" }
        val variants = schemaOf<Shape>(json).variants()
        variants.forEach { assertEquals("kind", it.strings("required").first()) }
    }

    @Test
    fun `ClassDiscriminatorMode NONE drops the discriminator from every variant`() {
        val json = Json(from = DefaultArgumentJson) { classDiscriminatorMode = ClassDiscriminatorMode.NONE }
        val variants = schemaOf<Payload>(json).variants()
        assertEquals(listOf("body"), variants.single().obj("properties").keys.toList())
    }

    @Test
    fun `the format's naming strategy renames properties and the required list`() {
        val json = Json(from = DefaultArgumentJson) { namingStrategy = JsonNamingStrategy.SnakeCase }
        val schema = schemaOf<Optionality>(json)
        assertEquals(listOf("required_here", "optional_here"), schema.obj("properties").keys.toList())
        assertEquals(listOf("required_here"), schema.strings("required"))
    }
}
