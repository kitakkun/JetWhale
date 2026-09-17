package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.HighlightNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.HighlightResult
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The highlight protocol on the wire. What has to hold is that a clear survives the trip: `nodeId`
 * is the one field whose absence means something — "take the box down" — and a null that decoded as
 * anything else would leave a box on the app's screen.
 */
class NodeHighlightSerializationTest {
    private val json = Json

    @Test
    fun `a request naming a node round-trips`() {
        val request = HighlightNode(rootId = "window-1", nodeId = -4, ttlMs = 30_000)
        assertEquals(request, json.decodeFromString<HighlightNode>(json.encodeToString(request)))
    }

    @Test
    fun `a request that clears the highlight round-trips with its null nodeId`() {
        val request = HighlightNode(rootId = "window-1", nodeId = null, ttlMs = 30_000)
        val decoded = json.decodeFromString<HighlightNode>(json.encodeToString(request))
        assertEquals(request, decoded)
        assertEquals(null, decoded.nodeId)
    }

    @Test
    fun `both answers round-trip`() {
        val shown = HighlightResult(shown = true)
        assertEquals(shown, json.decodeFromString<HighlightResult>(json.encodeToString(shown)))

        val refused = HighlightResult(shown = false, message = "unknown nodeId: -4")
        assertEquals(refused, json.decodeFromString<HighlightResult>(json.encodeToString(refused)))

        val deferred = HighlightResult(shown = false, message = "node 12 has no area", retryLater = true)
        assertEquals(deferred, json.decodeFromString<HighlightResult>(json.encodeToString(deferred)))
    }
}
