package com.kitakkun.jetwhale.protocol.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class WireSchemaTest {
    @Test
    fun `a dump renders back to the same text`() {
        val text = resourceText("wire/network-1.0.0-alpha12.wire")

        assertEquals(text, WireSchema.parse(text).render())
    }

    @Test
    fun `every field mark survives parsing`() {
        val field = WireField.parse("tags: list<string>? = …, omitted at default | also read as labels, tagList".substringAfter(": "))

        assertEquals(
            WireField(
                type = WireTypeRef("list", listOf(WireTypeRef("string", emptyList(), isNullable = false)), isNullable = true),
                isOptional = true,
                isOmittedAtDefault = true,
                alsoReadAs = setOf("labels", "tagList"),
            ),
            field,
        )
        assertEquals("list<string>? = …, omitted at default | also read as labels, tagList", field.render())
    }

    @Test
    fun `a nested type parses`() {
        assertEquals("map<string, list<sample.Item?>>?", WireTypeRef.parse("map<string, list<sample.Item?>>?").toString())
    }

    @Test
    fun `the hash leaves out the revision`() {
        val revision1 = WireSchema.parse(resourceText("wire/network-1.0.0-alpha12.wire"))
        val revision2 = revision1.copy(revision = 2)

        assertEquals(revision1.hash(), revision2.hash())
        assertEquals("7fa0c0b1889b36def689237baa82ffa980038f3267053e2c57e5cc6cfa659978", revision1.hash())
    }

    @Test
    fun `the hash changes with the schema`() {
        val alpha11 = WireSchema.parse(resourceText("wire/network-1.0.0-alpha11.wire"))
        val alpha12 = WireSchema.parse(resourceText("wire/network-1.0.0-alpha12.wire"))

        assertNotEquals(alpha11.hash(), alpha12.hash())
    }

    @Test
    fun `a dump with CRLF line endings parses to the same schema`() {
        val text = resourceText("wire/network-1.0.0-alpha12.wire")

        assertEquals(WireSchema.parse(text), WireSchema.parse(text.replace("\n", "\r\n")))
    }

    @Test
    fun `a dump without its revision line is rejected`() {
        assertFailsWith<IllegalArgumentException> { WireSchema.parse("event sample/ping\n") }
    }

    @Test
    fun `an unknown line is rejected`() {
        assertFailsWith<IllegalArgumentException> { WireSchema.parse("revision 1\n\nmessage sample/ping\n") }
    }

    private fun resourceText(path: String): String = checkNotNull(javaClass.classLoader.getResource(path)) { path }.readText()
}
