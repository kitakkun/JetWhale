package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The decisions the attribute layer makes that are not tied to a live `View`. The rest of it reads
 * and writes real views, which needs an instrumented device rather than this source set.
 */
class ViewAttributeSourceTest {
    @Test
    fun `reports a layout size that names a rule as that rule and offers both`() {
        val value = layoutSizeValue(constantName = "MATCH_PARENT", px = -1f, density = 2f)

        assertEquals(ViewAttributeValue.EnumValue("MATCH_PARENT", listOf("MATCH_PARENT", "WRAP_CONTENT")), value)
    }

    @Test
    fun `reports a layout size that is a length as a dimension in both pixels and dp`() {
        val value = layoutSizeValue(constantName = null, px = 48f, density = 2f)

        assertEquals(ViewAttributeValue.DimensionValue(px = 48f, dp = 24f), value)
    }

    @Test
    fun `a mismatched value names both what the attribute takes and what arrived`() {
        val message = wrongVariantMessage("visibility", "enum", ViewAttributeValue.BooleanValue(true))

        assertEquals("visibility expects a value of type 'enum', but a 'bool' value was sent", message)
    }

    @Test
    fun `every value variant has a name a message can use`() {
        val variants = listOf(
            ViewAttributeValue.BooleanValue(true) to "bool",
            ViewAttributeValue.IntValue(1) to "int",
            ViewAttributeValue.FloatValue(1f) to "float",
            ViewAttributeValue.TextValue("a") to "text",
            ViewAttributeValue.ColorValue(0) to "color",
            ViewAttributeValue.DimensionValue(px = 1f, dp = 1f) to "dimension",
            ViewAttributeValue.EnumValue("A", listOf("A")) to "enum",
        )

        for ((value, name) in variants) {
            assertEquals(name, variantNameOf(value))
        }
    }

    @Test
    fun `a Compose node id is explained rather than reported as missing`() {
        val message = noViewAttributesMessage(nodeId = 42)

        assertTrue(message.contains("Compose semantics node"), message)
    }

    @Test
    fun `a View node id that resolves to nothing says the tree has moved on`() {
        val message = noViewAttributesMessage(nodeId = -3)

        assertTrue(message.contains("capture the tree again"), message)
    }
}
