package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalJetWhaleApi::class)
class NodeAtCommandTest {
    @Test
    fun `reports the node a tap at the point is dispatched to`() {
        val json = nodeAt(x = 50, y = 20)

        assertEquals(7, json["node"]?.jsonObject?.get("id")?.jsonPrimitive?.content?.toInt())
        assertEquals("window", json["node"]?.jsonObject?.get("rootId")?.jsonPrimitive?.content)
    }

    @Test
    fun `a point with nothing interactive under it reports no node`() {
        val json = nodeAt(x = 500, y = 500)

        assertEquals(JsonNull, json["node"])
    }

    @Test
    fun `the window on top wins the point`() {
        val json = nodeAt(x = 50, y = 20, withDialog = true)

        assertEquals("dialog", json["node"]?.jsonObject?.get("rootId")?.jsonPrimitive?.content)
        assertEquals(9, json["node"]?.jsonObject?.get("id")?.jsonPrimitive?.content?.toInt())
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun nodeAt(x: Int, y: Int, withDialog: Boolean = false): JsonObject {
    val button = node(
        id = 7,
        isClickable = true,
        actions = listOf("OnClick"),
        bounds = NodeBounds(left = 0f, top = 0f, right = 100f, bottom = 40f),
    )
    val roots = buildList {
        add(
            root(
                "window",
                node = node(id = 0, bounds = NodeBounds(left = 0f, top = 0f, right = 400f, bottom = 800f), children = listOf(button)),
            ),
        )
        if (withDialog) {
            add(
                root(
                    "dialog",
                    node = node(
                        id = 8,
                        bounds = NodeBounds(left = 0f, top = 0f, right = 200f, bottom = 100f),
                        children = listOf(
                            node(
                                id = 9,
                                isClickable = true,
                                actions = listOf("OnClick"),
                                bounds = NodeBounds(left = 0f, top = 0f, right = 200f, bottom = 100f),
                            ),
                        ),
                    ),
                ),
            )
        }
    }
    val command = NodeAtCommand(capture = { snapshot(*roots.toTypedArray()) })
    val result = runBlocking {
        command.execute(
            JetWhaleMcpArguments(
                buildJsonObject {
                    put("x", x)
                    put("y", y)
                },
            ),
        )
    }
    return Json.parseToJsonElement(result).jsonObject
}
