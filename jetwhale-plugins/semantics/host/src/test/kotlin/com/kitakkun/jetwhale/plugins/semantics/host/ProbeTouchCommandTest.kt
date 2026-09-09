package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.TouchProbeResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalJetWhaleApi::class)
class ProbeTouchCommandTest {
    @Test
    fun `a touch the app takes where a node is expected agrees`() {
        val json = probe(x = 50, y = 20, consumed = true)

        assertEquals(true, json["consumed"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(true, json["agrees"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(7, json["expected"]?.jsonObject?.get("id")?.jsonPrimitive?.content?.toInt())
        assertNull(json["note"])
    }

    @Test
    fun `a touch taken where the tree shows nothing names the overlay it cannot see`() {
        val json = probe(x = 300, y = 700, consumed = true)

        assertEquals(JsonNull, json["expected"])
        assertEquals(false, json["agrees"]?.jsonPrimitive?.content?.toBoolean())
        assertContains(json["note"]!!.jsonPrimitive.content, "does not show")
    }

    @Test
    fun `a node the app takes no touch for is reported the other way round`() {
        val json = probe(x = 50, y = 20, consumed = false)

        assertEquals(false, json["agrees"]?.jsonPrimitive?.content?.toBoolean())
        assertContains(json["note"]!!.jsonPrimitive.content, "took no touch")
    }

    @Test
    fun `a platform that cannot probe reports why instead of a verdict`() {
        val json = probe(x = 50, y = 20, consumed = false, message = "no window can be probed")

        assertEquals("no window can be probed", json["note"]?.jsonPrimitive?.content)
    }
}

private fun probe(x: Int, y: Int, consumed: Boolean, message: String? = null): JsonObject {
    val button = node(id = 7, isClickable = true, actions = listOf("OnClick"), bounds = NodeBounds(0f, 0f, 100f, 40f))
    val roots = listOf(
        root("window", node = node(id = 0, bounds = NodeBounds(0f, 0f, 400f, 800f), children = listOf(button))),
    )
    val command = ProbeTouchCommand(
        capture = { snapshot(*roots.toTypedArray()) },
        probe = { TouchProbeResult(consumed = consumed, rootId = "window", message = message) },
    )
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
