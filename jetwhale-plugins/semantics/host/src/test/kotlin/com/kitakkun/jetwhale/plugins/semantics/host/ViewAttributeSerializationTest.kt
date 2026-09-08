package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.GetViewAttributes
import com.kitakkun.jetwhale.plugins.semantics.protocol.SetViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResponse
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The attribute protocol on the wire. Host and agent are built against the same types, so what has
 * to hold is that every value variant survives the trip and that each keeps its own discriminator —
 * a variant that decoded as another one would silently write the wrong property.
 */
private val LAYOUT_SIZE_CONSTANTS = listOf("MATCH_PARENT", "WRAP_CONTENT")

class ViewAttributeSerializationTest {
    private val json = Json

    private val variants = listOf(
        ViewAttributeValue.BooleanValue(true) to "bool",
        ViewAttributeValue.IntValue(-3) to "int",
        ViewAttributeValue.FloatValue(0.5f) to "float",
        ViewAttributeValue.TextValue("two words") to "text",
        ViewAttributeValue.ColorValue(0x80FF0000.toInt()) to "color",
        ViewAttributeValue.DimensionValue(px = 48f, dp = 24f) to "dimension",
        ViewAttributeValue.EnumValue("GONE", listOf("VISIBLE", "INVISIBLE", "GONE")) to "enum",
        // Both cases of the one variant, because it is the pair of them that has to survive.
        ViewAttributeValue.LayoutSizeValue(constant = "WRAP_CONTENT", px = null, dp = null, constants = LAYOUT_SIZE_CONSTANTS) to "layoutSize",
        ViewAttributeValue.LayoutSizeValue(constant = null, px = 500f, dp = 250f, constants = LAYOUT_SIZE_CONSTANTS) to "layoutSize",
    )

    @Test
    fun `every value variant round-trips as itself`() {
        for ((value, _) in variants) {
            assertEquals(value, json.decodeFromString<ViewAttributeValue>(json.encodeToString<ViewAttributeValue>(value)))
        }
    }

    @Test
    fun `every value variant travels under its own discriminator`() {
        for ((value, discriminator) in variants) {
            val encoded = json.parseToJsonElement(json.encodeToString<ViewAttributeValue>(value)).jsonObject
            assertEquals(discriminator, encoded["type"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `a snapshot round-trips with its attributes`() {
        val snapshot = ViewAttributeSnapshot(
            rootId = "window-1",
            nodeId = -4,
            viewClass = "android.widget.TextView",
            attributes = variants.mapIndexed { index, (value, name) ->
                ViewAttribute(id = "attribute-$index", label = name, group = "State", value = value, editable = index % 2 == 0)
            },
        )

        assertEquals(snapshot, json.decodeFromString<ViewAttributeSnapshot>(json.encodeToString(snapshot)))
    }

    @Test
    fun `the requests and their answers round-trip`() {
        val get = GetViewAttributes(rootId = "window-1", nodeId = -4)
        assertEquals(get, json.decodeFromString<GetViewAttributes>(json.encodeToString(get)))

        val set = SetViewAttribute(rootId = "window-1", nodeId = -4, attributeId = "visibility", value = ViewAttributeValue.EnumValue("GONE", listOf("GONE")))
        assertEquals(set, json.decodeFromString<SetViewAttribute>(json.encodeToString(set)))

        val absent = ViewAttributeResponse(snapshot = null, message = "this root has no View attributes")
        assertEquals(absent, json.decodeFromString<ViewAttributeResponse>(json.encodeToString(absent)))

        val refused = ViewAttributeResult(applied = false, message = "read-only")
        assertEquals(refused, json.decodeFromString<ViewAttributeResult>(json.encodeToString(refused)))
    }
}
