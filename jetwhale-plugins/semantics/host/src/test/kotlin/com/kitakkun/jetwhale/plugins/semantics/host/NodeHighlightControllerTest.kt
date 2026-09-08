package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.HighlightNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.HighlightResult
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The decisions behind the on-device highlight, driven without a composition: what the host wants
 * shown, and what it actually sends to get there.
 */
class NodeHighlightControllerTest {
    private val firstRoot = NodeKey(rootId = "window-1", nodeId = -4)
    private val secondRoot = NodeKey(rootId = "window-2", nodeId = 12)

    // -- highlightTarget -------------------------------------------------------

    @Test
    fun `the selected node is highlighted when nothing is hovered`() {
        assertEquals(firstRoot, highlightTarget(enabled = true, selected = firstRoot, hovered = null))
    }

    @Test
    fun `a hovered row overrides the selection`() {
        assertEquals(secondRoot, highlightTarget(enabled = true, selected = firstRoot, hovered = secondRoot))
    }

    @Test
    fun `leaving a hovered row goes back to the selection`() {
        val whileHovering = highlightTarget(enabled = true, selected = firstRoot, hovered = secondRoot)
        val afterLeaving = highlightTarget(enabled = true, selected = firstRoot, hovered = null)
        assertEquals(secondRoot, whileHovering)
        assertEquals(firstRoot, afterLeaving)
    }

    @Test
    fun `the toggle being off highlights nothing at all`() {
        assertEquals(null, highlightTarget(enabled = false, selected = firstRoot, hovered = secondRoot))
    }

    @Test
    fun `nothing is highlighted with nothing selected and nothing hovered`() {
        assertEquals(null, highlightTarget(enabled = true, selected = null, hovered = null))
    }

    // -- NodeHighlightController ----------------------------------------------

    @Test
    fun `showing a node sends it with the shared time to live`() = runBlocking {
        val recorder = Recorder()
        val controller = recorder.controller()

        assertTrue(controller.show(firstRoot))
        assertEquals(
            listOf(HighlightNode(rootId = "window-1", nodeId = -4, ttlMs = HIGHLIGHT_TTL_MILLIS)),
            recorder.sent,
        )
        assertNull(controller.statusMessage)
    }

    @Test
    fun `moving the highlight to another root clears the one being left`() = runBlocking {
        val recorder = Recorder()
        val controller = recorder.controller()

        controller.show(firstRoot)
        controller.show(secondRoot)

        assertEquals(
            listOf(
                HighlightNode(rootId = "window-1", nodeId = -4, ttlMs = HIGHLIGHT_TTL_MILLIS),
                HighlightNode(rootId = "window-1", nodeId = null, ttlMs = HIGHLIGHT_TTL_MILLIS),
                HighlightNode(rootId = "window-2", nodeId = 12, ttlMs = HIGHLIGHT_TTL_MILLIS),
            ),
            recorder.sent,
        )
    }

    @Test
    fun `moving the highlight within one root does not clear it first`() = runBlocking {
        val recorder = Recorder()
        val controller = recorder.controller()

        controller.show(firstRoot)
        controller.show(firstRoot.copy(nodeId = -9))

        assertEquals(listOf(-4, -9), recorder.sent.map { it.nodeId })
    }

    @Test
    fun `clearing sends a null node id to the root that was showing one`() = runBlocking {
        val recorder = Recorder()
        val controller = recorder.controller()

        controller.show(firstRoot)
        controller.clear()

        assertEquals(HighlightNode(rootId = "window-1", nodeId = null, ttlMs = HIGHLIGHT_TTL_MILLIS), recorder.sent.last())
    }

    @Test
    fun `clearing with nothing showing sends nothing`() = runBlocking {
        val recorder = Recorder()

        recorder.controller().clear()

        assertTrue(recorder.sent.isEmpty())
    }

    @Test
    fun `a root that refuses keeps its message and is not worth renewing`() = runBlocking {
        val recorder = Recorder(answer = { HighlightResult(shown = false, message = "unknown nodeId: -4") })
        val controller = recorder.controller()

        assertFalse(controller.show(firstRoot))
        assertEquals("unknown nodeId: -4", controller.statusMessage)
    }

    @Test
    fun `a refusal leaves nothing to clear on the next node`() = runBlocking {
        val recorder = Recorder(answer = { HighlightResult(shown = false, message = "no window") })
        val controller = recorder.controller()

        controller.show(firstRoot)
        controller.show(secondRoot)

        assertEquals(listOf("window-1", "window-2"), recorder.sent.map { it.rootId })
    }

    @Test
    fun `an app that does not answer reports the failure instead of throwing`() = runBlocking {
        val recorder = Recorder(answer = { throw JetWhaleMessagingException("the session is gone") })
        val controller = recorder.controller()

        assertFalse(controller.show(firstRoot))
        assertEquals("Highlight failed: the session is gone", controller.statusMessage)
    }

    private class Recorder(private val answer: (HighlightNode) -> HighlightResult = { HighlightResult(shown = true) }) {
        val sent = mutableListOf<HighlightNode>()

        fun controller(): NodeHighlightController = NodeHighlightController(
            scope = CoroutineScope(Dispatchers.Unconfined),
            send = { request ->
                sent += request
                answer(request)
            },
        )
    }
}
