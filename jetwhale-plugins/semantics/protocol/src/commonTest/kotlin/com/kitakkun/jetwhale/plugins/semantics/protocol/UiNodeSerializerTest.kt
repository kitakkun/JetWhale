package com.kitakkun.jetwhale.plugins.semantics.protocol

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.serialization.JetWhaleJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(InternalJetWhaleApi::class)
class UiNodeSerializerTest {
    private val bounds = NodeBounds(0f, 0f, 100f, 40f)

    @Test
    fun `a View node round-trips through the wire format`() {
        val node: UiNode = ViewNode(
            id = -1,
            viewClass = "android.widget.Button",
            resourceId = "submit",
            text = "Send",
            bounds = bounds,
            boundsInScreen = bounds,
            actions = listOf("OnClick"),
            isClickable = true,
            children = listOf(ComposeNode(id = 2, bounds = bounds, boundsInScreen = bounds)),
        )

        val encoded = JetWhaleJson.encodeToString(UiNodeSerializer, node)

        assertEquals("view", JetWhaleJson.parseToJsonElement(encoded).jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals(node, JetWhaleJson.decodeFromString(UiNodeSerializer, encoded))
    }

    @Test
    fun `a node naming no type decodes as a Compose node`() {
        // What an agent that predates the two node types writes: the shared fields and nothing
        // saying which type they belong to.
        val json = """{"id":7,"role":"Button","text":"Send","bounds":$BOUNDS_JSON,"boundsInScreen":$BOUNDS_JSON}"""

        val node = JetWhaleJson.decodeFromString(UiNodeSerializer, json)

        val compose = assertIs<ComposeNode>(node)
        assertEquals(7, compose.id)
        assertEquals("Button", compose.role)
        assertEquals("Send", compose.text)
    }

    @Test
    fun `a node typed view decodes as a View node`() {
        val json = """{"type":"view","id":-3,"viewClass":"android.widget.TextView","resourceId":"status","bounds":$BOUNDS_JSON,"boundsInScreen":$BOUNDS_JSON}"""

        val node = JetWhaleJson.decodeFromString(UiNodeSerializer, json)

        val view = assertIs<ViewNode>(node)
        assertEquals(-3, view.id)
        assertEquals("android.widget.TextView", view.viewClass)
        assertEquals("status", view.resourceId)
    }

    @Test
    fun `a root carries the type of every node in its tree`() {
        val root = ComposeRoot(
            rootId = "window",
            label = "MainActivity",
            density = 2f,
            windowOffsetX = 0f,
            windowOffsetY = 0f,
            node = ComposeNode(
                id = 1,
                bounds = bounds,
                boundsInScreen = bounds,
                children = listOf(ViewNode(id = -1, viewClass = "android.widget.Button", bounds = bounds, boundsInScreen = bounds)),
            ),
        )

        val decoded = JetWhaleJson.decodeFromString<ComposeRoot>(JetWhaleJson.encodeToString(root))

        assertEquals(root, decoded)
    }
}

private const val BOUNDS_JSON = """{"left":0.0,"top":0.0,"right":100.0,"bottom":40.0}"""
