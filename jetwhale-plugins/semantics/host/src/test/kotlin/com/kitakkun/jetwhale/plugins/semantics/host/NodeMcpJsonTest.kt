package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeMcpJsonTest {
    @Test
    fun `emits the identifying semantics and a ready-made tap point`() {
        val json = node(
            id = 7,
            role = "Button",
            text = "Send",
            testTag = "send-button",
            actions = listOf("OnClick"),
            isClickable = true,
            bounds = NodeBounds(10f, 20f, 110f, 60f),
        ).toMcpJson()

        assertEquals(7, json["id"]?.jsonPrimitive?.content?.toInt())
        assertEquals("Button", json["role"]?.jsonPrimitive?.content)
        assertEquals("Send", json["text"]?.jsonPrimitive?.content)
        assertEquals("send-button", json["testTag"]?.jsonPrimitive?.content)
        assertEquals(listOf("OnClick"), json["actions"]?.jsonArray?.map { it.jsonPrimitive.content })
        assertEquals(10, json["bounds"]?.jsonObject?.get("left")?.jsonPrimitive?.content?.toInt())
        assertEquals(60, json["tap"]?.jsonObject?.get("x")?.jsonPrimitive?.content?.toInt())
        assertEquals(40, json["tap"]?.jsonObject?.get("y")?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `omits the unsurprising side of every flag so a large tree stays readable`() {
        val json = node(id = 1, text = "label").toMcpJson()

        assertNull(json["clickable"])
        assertNull(json["enabled"])
        assertNull(json["focused"])
        assertNull(json["visible"])
        assertNull(json["actions"])
        assertNull(json["role"])
    }

    @Test
    fun `spells out a disabled or invisible node, which is the surprising case`() {
        val json = node(id = 1, isEnabled = false, isVisible = false).toMcpJson()

        assertFalse(json["enabled"]!!.jsonPrimitive.content.toBoolean())
        assertFalse(json["visible"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `omits the tap point for a node with no area to tap`() {
        val json = node(id = 1, bounds = NodeBounds(0f, 0f, 0f, 0f)).toMcpJson()

        assertNull(json["tap"])
    }

    @Test
    fun `carries rootId on a flat result so the node stays addressable`() {
        val json = node(id = 1, children = listOf(node(id = 2))).toMcpJson(rootId = "window", includeChildren = false)

        assertEquals("window", json["rootId"]?.jsonPrimitive?.content)
        assertNull(json["children"])
    }

    @Test
    fun `renders a snapshot with its roots and only reports warnings when there are any`() {
        val json = snapshot(root("window", label = "MainActivity", node = node(id = 1))).toMcpJson()

        assertEquals(1, json["roots"]?.jsonArray?.size)
        val root = json["roots"]!!.jsonArray.single().jsonObject
        assertEquals("window", root["rootId"]?.jsonPrimitive?.content)
        assertEquals("MainActivity", root["label"]?.jsonPrimitive?.content)
        assertTrue(root["node"] is JsonObject)
        assertNull(json["warnings"])
    }

    @Test
    fun `reports where a root's window sits on screen`() {
        // A dialog's window is not at the screen origin, which is the case where node coordinates
        // would drift if they were reported window-relative — so the offset is worth surfacing.
        val dialog = root("dialog", node = node(id = 1)).copy(windowOffsetX = 120.4f, windowOffsetY = 926.6f)

        val offset = snapshot(dialog).toMcpJson()["roots"]!!.jsonArray.single().jsonObject["windowOffset"]!!.jsonObject

        assertEquals(120, offset["x"]?.jsonPrimitive?.content?.toInt())
        assertEquals(927, offset["y"]?.jsonPrimitive?.content?.toInt())
    }
}
