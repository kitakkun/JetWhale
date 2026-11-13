package com.kitakkun.jetwhale.protocol.gradle

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProtocolRevisionRuleTest {
    @Test
    fun `the first dump is at revision 1`() {
        assertNull(ProtocolRevisionRule.refusalOf(previous = null, next = schema(revision = 1, ENCODING_WITH_TEXT)))
        assertNotNull(ProtocolRevisionRule.refusalOf(previous = null, next = schema(revision = 2, ENCODING_WITH_TEXT)))
    }

    @Test
    fun `a breaking change at the same revision is refused with the revision to set`() {
        val refusal = ProtocolRevisionRule.refusalOf(schema(revision = 3, ENCODING_WITH_TEXT), schema(revision = 3, ENCODING_WITH_HEX))

        assertNotNull(refusal)
        assertContains(refusal, "sample/ping.encoding: enum entry HEX added")
        assertContains(refusal, "Set protocolRevision to 4")
    }

    @Test
    fun `a breaking change with a raised revision passes`() {
        assertNull(ProtocolRevisionRule.refusalOf(schema(revision = 1, ENCODING_WITH_TEXT), schema(revision = 2, ENCODING_WITH_HEX)))
    }

    @Test
    fun `a revision may skip numbers`() {
        assertNull(ProtocolRevisionRule.refusalOf(schema(revision = 1, ENCODING_WITH_TEXT), schema(revision = 3, ENCODING_WITH_HEX)))
    }

    @Test
    fun `an additive change at the same revision passes`() {
        val previous = schema(revision = 1, ENCODING_WITH_TEXT)
        val withNewEvent = WireSchema.parse(
            previous.render().replace("event sample/ping\n", "event sample/ping\nevent sample/pinged\n") + "\nobject sample/pinged\n",
        )

        assertNull(ProtocolRevisionRule.refusalOf(previous, withNewEvent))
    }

    @Test
    fun `a lowered revision is refused even without a change`() {
        val refusal = ProtocolRevisionRule.refusalOf(schema(revision = 2, ENCODING_WITH_TEXT), schema(revision = 1, ENCODING_WITH_TEXT))

        assertNotNull(refusal)
        assertContains(refusal, "went down from 2 to 1")
    }

    private fun schema(revision: Int, encodingEntries: List<String>): WireSchema = WireSchema.parse(
        (
            listOf("revision $revision", "", "event sample/ping", "", "enum sample.Encoding") +
                encodingEntries.map { "  $it" } +
                listOf("", "class sample/ping", "  encoding: sample.Encoding")
            ).joinToString("\n", postfix = "\n"),
    )

    private companion object {
        val ENCODING_WITH_TEXT = listOf("TEXT")
        val ENCODING_WITH_HEX = listOf("HEX", "TEXT")
    }
}
