package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.semantics.protocol.GetViewAttributes
import com.kitakkun.jetwhale.plugins.semantics.protocol.SetViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResponse
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeValue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.run(arguments: JsonObject): JsonObject = runBlocking {
    Json.parseToJsonElement(execute(JetWhaleMcpArguments(arguments))).jsonObject
}

private fun attribute(
    id: String,
    value: ViewAttributeValue,
    group: String = "State",
    editable: Boolean = true,
): ViewAttribute = ViewAttribute(id = id, label = id, group = group, value = value, editable = editable)

private fun response(vararg attributes: ViewAttribute): ViewAttributeResponse = ViewAttributeResponse(
    snapshot = ViewAttributeSnapshot(
        rootId = "window-1",
        nodeId = -4,
        viewClass = "android.widget.TextView",
        attributes = attributes.toList(),
    ),
)

private fun arguments(vararg pairs: Pair<String, Any>): JsonObject = buildJsonObject {
    for ((key, value) in pairs) {
        when (value) {
            is Int -> put(key, value)
            else -> put(key, value.toString())
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
class ViewAttributeCommandsTest {
    @Test
    fun `getViewAttributes reports each attribute with the type that says how to write it`() {
        val command = GetViewAttributesCommand(
            getAttributes = {
                response(
                    attribute("visibility", ViewAttributeValue.EnumValue("VISIBLE", listOf("VISIBLE", "INVISIBLE", "GONE"))),
                    attribute("padding.left", ViewAttributeValue.DimensionValue(px = 48f, dp = 24f), group = "Layout"),
                )
            },
        )

        val result = command.run(arguments("rootId" to "window-1", "nodeId" to -4))

        assertEquals("android.widget.TextView", result["viewClass"]?.jsonPrimitive?.content)
        val rows = result["attributes"]!!.jsonArray.map { it.jsonObject }
        assertEquals("enum", rows[0]["type"]?.jsonPrimitive?.content)
        assertEquals("VISIBLE", rows[0]["value"]?.jsonPrimitive?.content)
        assertEquals(listOf("VISIBLE", "INVISIBLE", "GONE"), rows[0]["options"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("dimension", rows[1]["type"]?.jsonPrimitive?.content)
        assertEquals("48.0", rows[1]["value"]?.jsonPrimitive?.content)
        assertEquals(24f, rows[1]["dp"]?.jsonPrimitive?.content?.toFloat())
    }

    @Test
    fun `getViewAttributes marks only the read-only attributes, which are the exception`() {
        val command = GetViewAttributesCommand(
            getAttributes = {
                response(
                    attribute("enabled", ViewAttributeValue.BooleanValue(true)),
                    attribute("class", ViewAttributeValue.TextValue("android.widget.TextView"), group = "Info", editable = false),
                )
            },
        )

        val rows = command.run(arguments("rootId" to "window-1", "nodeId" to -4))["attributes"]!!.jsonArray.map { it.jsonObject }

        assertNull(rows[0]["editable"])
        assertEquals(false, rows[1]["editable"]?.jsonPrimitive?.content?.toBoolean())
    }

    @Test
    fun `getViewAttributes passes on the reason a node has none instead of failing`() {
        val command = GetViewAttributesCommand(
            getAttributes = { ViewAttributeResponse(snapshot = null, message = "node 42 is a Compose semantics node, which has no View attributes") },
        )

        val result = command.run(arguments("rootId" to "window-1", "nodeId" to 42))

        assertTrue(result["message"]!!.jsonPrimitive.content.contains("Compose semantics node"))
        assertNull(result["attributes"])
    }

    @Test
    fun `setViewAttribute reads the string as the type the attribute currently has`() {
        var sent: SetViewAttribute? = null
        val command = SetViewAttributeCommand(
            getAttributes = { response(attribute("visibility", ViewAttributeValue.EnumValue("VISIBLE", listOf("VISIBLE", "INVISIBLE", "GONE")))) },
            setAttribute = { request ->
                sent = request
                ViewAttributeResult(
                    applied = true,
                    attribute = attribute("visibility", ViewAttributeValue.EnumValue("GONE", listOf("VISIBLE", "INVISIBLE", "GONE"))),
                )
            },
        )

        val result = command.run(arguments("rootId" to "window-1", "nodeId" to -4, "attributeId" to "visibility", "value" to "gone"))

        assertEquals(ViewAttributeValue.EnumValue("GONE", listOf("VISIBLE", "INVISIBLE", "GONE")), sent?.value)
        assertTrue(result["applied"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("GONE", result["attribute"]!!.jsonObject["value"]?.jsonPrimitive?.content)
    }

    @Test
    fun `setViewAttribute names the ids the view does have when the one asked for is unknown`() {
        val command = SetViewAttributeCommand(
            getAttributes = { response(attribute("enabled", ViewAttributeValue.BooleanValue(true))) },
            setAttribute = { ViewAttributeResult(applied = true) },
        )

        val failure = assertFailsWith<JetWhaleMcpArgumentException> {
            command.run(arguments("rootId" to "window-1", "nodeId" to -4, "attributeId" to "colour", "value" to "red"))
        }

        assertTrue(failure.message!!.contains("unknown attributeId: colour"), failure.message!!)
        assertTrue(failure.message!!.contains("enabled"), failure.message!!)
    }

    @Test
    fun `setViewAttribute refuses a read-only attribute before reaching the app`() {
        var reached = false
        val command = SetViewAttributeCommand(
            getAttributes = { response(attribute("bounds", ViewAttributeValue.TextValue("0,0,10,10"), group = "Layout", editable = false)) },
            setAttribute = {
                reached = true
                ViewAttributeResult(applied = true)
            },
        )

        val failure = assertFailsWith<JetWhaleMcpArgumentException> {
            command.run(arguments("rootId" to "window-1", "nodeId" to -4, "attributeId" to "bounds", "value" to "1,1,2,2"))
        }

        assertTrue(failure.message!!.contains("read-only"), failure.message!!)
        assertEquals(false, reached)
    }

    @Test
    fun `setViewAttribute reports a refusal from the app rather than throwing`() {
        val command = SetViewAttributeCommand(
            getAttributes = { response(attribute("alpha", ViewAttributeValue.FloatValue(1f), group = "Appearance")) },
            setAttribute = { ViewAttributeResult(applied = false, message = "the app rejected the change") },
        )

        val result = command.run(arguments("rootId" to "window-1", "nodeId" to -4, "attributeId" to "alpha", "value" to "0.5"))

        assertEquals(false, result["applied"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("the app rejected the change", result["message"]?.jsonPrimitive?.content)
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
class ViewAttributeValueParsingTest {
    @Test
    fun `reads a boolean, an int and a float as their own type`() {
        assertEquals(
            ViewAttributeValue.BooleanValue(true),
            parseViewAttributeValue("enabled", ViewAttributeValue.BooleanValue(false), "true"),
        )
        assertEquals(
            ViewAttributeValue.IntValue(3),
            parseViewAttributeValue("maxLines", ViewAttributeValue.IntValue(1), "3"),
        )
        assertEquals(
            ViewAttributeValue.FloatValue(0.5f),
            parseViewAttributeValue("alpha", ViewAttributeValue.FloatValue(1f), "0.5"),
        )
    }

    @Test
    fun `takes a text value exactly as typed, spaces included`() {
        assertEquals(
            ViewAttributeValue.TextValue("  two words  "),
            parseViewAttributeValue("text", ViewAttributeValue.TextValue("old"), "  two words  "),
        )
    }

    @Test
    fun `reads a dimension as a pixel figure`() {
        assertEquals(
            ViewAttributeValue.DimensionValue(px = 48f, dp = 48f),
            parseViewAttributeValue("padding.left", ViewAttributeValue.DimensionValue(px = 0f, dp = 0f), "48"),
        )
    }

    @Test
    fun `accepts either shape for a layout size, which reads as either`() {
        // Written where it currently reads as a length…
        assertEquals(
            ViewAttributeValue.EnumValue("MATCH_PARENT", emptyList()),
            parseViewAttributeValue("layout.width", ViewAttributeValue.DimensionValue(px = 100f, dp = 50f), "match_parent"),
        )
        // …and where it currently reads as one of the two constants.
        assertEquals(
            ViewAttributeValue.DimensionValue(px = 100f, dp = 100f),
            parseViewAttributeValue("layout.width", ViewAttributeValue.EnumValue("WRAP_CONTENT", listOf("MATCH_PARENT", "WRAP_CONTENT")), "100"),
        )
    }

    @Test
    fun `matches an enum option whatever case it was typed in`() {
        assertEquals(
            ViewAttributeValue.EnumValue("INVISIBLE", listOf("VISIBLE", "INVISIBLE", "GONE")),
            parseViewAttributeValue("visibility", ViewAttributeValue.EnumValue("VISIBLE", listOf("VISIBLE", "INVISIBLE", "GONE")), "invisible"),
        )
    }

    @Test
    fun `names the options when an enum value is not one of them`() {
        val failure = assertFailsWith<JetWhaleMcpArgumentException> {
            parseViewAttributeValue("visibility", ViewAttributeValue.EnumValue("VISIBLE", listOf("VISIBLE", "GONE")), "hidden")
        }

        assertTrue(failure.message!!.contains("VISIBLE, GONE"), failure.message!!)
    }

    @Test
    fun `says what a number should have looked like`() {
        val failure = assertFailsWith<JetWhaleMcpArgumentException> {
            parseViewAttributeValue("maxLines", ViewAttributeValue.IntValue(1), "a few")
        }

        assertTrue(failure.message!!.contains("expected a whole number"), failure.message!!)
    }

    @Test
    fun `reads a color as AARRGGBB, and a six-digit one as opaque`() {
        assertEquals(0x80FF0000.toInt(), parseArgb("#80FF0000"))
        assertEquals(0xFF0000FFu.toInt(), parseArgb("#0000FF"))
        assertEquals(0xFF0000FFu.toInt(), parseArgb("0000ff"))
    }

    @Test
    fun `refuses a color that is not hex or is the wrong length`() {
        assertNull(parseArgb("#GGGGGG"))
        assertNull(parseArgb("#FFF"))
        assertNull(parseArgb("blue"))
    }

    @Test
    fun `writes a color back in the notation it is read in`() {
        assertEquals("#80FF0000", formatArgb(0x80FF0000.toInt()))
        assertEquals("#FF0000FF", formatArgb(0xFF0000FFu.toInt()))
    }
}
