package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeType
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeValue
import com.kitakkun.jetwhale.plugins.semantics.protocol.type
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [ViewAttributeType] to what actually travels.
 *
 * The type list is what the tool descriptions are built from, so it is only worth having if it
 * cannot fall out of step with the wire format or with what a read reports.
 */
class ViewAttributeTypeTest {

    /** One value of every type, so a new variant makes the `when` in `sampleOf` fail to compile. */
    private fun sampleOf(type: ViewAttributeType): ViewAttributeValue = when (type) {
        ViewAttributeType.Bool -> ViewAttributeValue.BooleanValue(value = true)

        ViewAttributeType.Int -> ViewAttributeValue.IntValue(value = 2)

        ViewAttributeType.Float -> ViewAttributeValue.FloatValue(value = 0.5f)

        ViewAttributeType.Text -> ViewAttributeValue.TextValue(value = "sample")

        ViewAttributeType.Color -> ViewAttributeValue.ColorValue(argb = 0xFF102030.toInt())

        ViewAttributeType.Dimension -> ViewAttributeValue.DimensionValue(px = 48f, dp = 16f)

        ViewAttributeType.Enum -> ViewAttributeValue.EnumValue(value = "ONE", options = listOf("ONE", "TWO"))

        ViewAttributeType.LayoutSize -> ViewAttributeValue.LayoutSizeValue(
            constant = null,
            px = 500f,
            dp = 166.7f,
            constants = listOf("MATCH_PARENT", "WRAP_CONTENT"),
        )
    }

    private fun attributeOf(type: ViewAttributeType) = ViewAttribute(
        id = "sample",
        label = "sample",
        group = "State",
        value = sampleOf(type),
        editable = true,
    )

    @Test
    fun `every type's wire name is the serial name its value is encoded under`() {
        for (type in ViewAttributeType.entries) {
            val encoded = Json.encodeToJsonElement(ViewAttributeValue.serializer(), sampleOf(type))
            assertEquals(type.wireName, encoded.jsonObject.getValue("type").jsonPrimitive.content, "for $type")
        }
    }

    @Test
    fun `every type declares the fields a read reports beside the value`() {
        val alwaysPresent = setOf("id", "label", "group", "type", "value")
        for (type in ViewAttributeType.entries) {
            val reported = attributeOf(type).toMcpJson().keys - alwaysPresent
            assertEquals(type.extraFields.toSet(), reported, "for $type")
        }
    }

    @Test
    fun `every type is named in the value a read reports it under`() {
        for (type in ViewAttributeType.entries) {
            assertEquals(type, sampleOf(type).type, "for $type")
            assertEquals(type.wireName, sampleOf(type).typeName, "for $type")
        }
    }
}
