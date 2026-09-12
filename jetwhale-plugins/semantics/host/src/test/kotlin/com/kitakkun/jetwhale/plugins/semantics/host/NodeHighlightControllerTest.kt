package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.HighlightNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.HighlightResult
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The on-device highlight driven without a composition: what the host sends to keep it in step. */
class NodeHighlightControllerTest {
    private val firstRoot = NodeKey(rootId = "window-1", nodeId = -4)
    private val secondRoot = NodeKey(rootId = "window-2", nodeId = 12)

    // -- NodeHighlightController ----------------------------------------------

    @Test
    fun `showing a node sends it with the shared time to live`() = runBlocking {
        val recorder = Recorder()
        val controller = recorder.controller()

        assertTrue(controller.show(firstRoot).shown)
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
        controller.show(null)

        assertEquals(HighlightNode(rootId = "window-1", nodeId = null, ttlMs = HIGHLIGHT_TTL_MILLIS), recorder.sent.last())
    }

    @Test
    fun `clearing with nothing showing sends nothing`() = runBlocking {
        val recorder = Recorder()

        recorder.controller().show(null)

        assertTrue(recorder.sent.isEmpty())
    }

    @Test
    fun `a root that refuses keeps its message and is not worth renewing`() = runBlocking {
        val recorder = Recorder(answer = { HighlightResult(shown = false, message = "unknown nodeId: -4") })
        val controller = recorder.controller()

        assertFalse(controller.show(firstRoot).shown)
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

        assertFalse(controller.show(firstRoot).shown)
        assertEquals("Highlight failed: the session is gone", controller.statusMessage)
    }

    // -- setTarget, on virtual time -------------------------------------------

    @Test
    fun `a target replaced within the debounce is never sent`() = runTest {
        val recorder = Recorder()
        val controller = recorder.controller(backgroundScope)

        controller.setTarget(firstRoot)
        advanceTimeBy(HIGHLIGHT_HOVER_DEBOUNCE_MILLIS / 2)
        controller.setTarget(secondRoot)
        advanceTimeBy(HIGHLIGHT_HOVER_DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(listOf(secondRoot.nodeId), recorder.sent.map { it.nodeId })
    }

    @Test
    fun `a standing target is renewed before the app forgets it`() = runTest {
        val recorder = Recorder()
        val controller = recorder.controller(backgroundScope)

        controller.setTarget(firstRoot)
        advanceTimeBy(HIGHLIGHT_HOVER_DEBOUNCE_MILLIS)
        runCurrent()
        assertEquals(1, recorder.sent.size)

        advanceTimeBy(HIGHLIGHT_RENEWAL_MILLIS)
        runCurrent()
        assertEquals(listOf(firstRoot.nodeId, firstRoot.nodeId), recorder.sent.map { it.nodeId })
    }

    @Test
    fun `dropping the target clears the highlight without waiting`() = runTest {
        val recorder = Recorder()
        val controller = recorder.controller(backgroundScope)

        controller.setTarget(firstRoot)
        advanceTimeBy(HIGHLIGHT_HOVER_DEBOUNCE_MILLIS)
        runCurrent()
        controller.setTarget(null)
        runCurrent()

        assertEquals(HighlightNode(rootId = "window-1", nodeId = null, ttlMs = HIGHLIGHT_TTL_MILLIS), recorder.sent.last())
        advanceTimeBy(HIGHLIGHT_RENEWAL_MILLIS)
        runCurrent()
        assertEquals(2, recorder.sent.size)
    }

    @Test
    fun `a refusal the app expects to lift keeps the target renewed`() = runTest {
        val recorder = Recorder(answer = { HighlightResult(shown = false, message = "node -4 has no area", retryLater = true) })
        val controller = recorder.controller(backgroundScope)

        controller.setTarget(firstRoot)
        advanceTimeBy(HIGHLIGHT_HOVER_DEBOUNCE_MILLIS + HIGHLIGHT_RENEWAL_MILLIS * 2)
        runCurrent()

        assertEquals(3, recorder.sent.size)
        assertEquals("node -4 has no area", controller.statusMessage)
    }

    @Test
    fun `a plain refusal stops the renewal`() = runTest {
        val recorder = Recorder(answer = { HighlightResult(shown = false, message = "unknown nodeId: -4") })
        val controller = recorder.controller(backgroundScope)

        controller.setTarget(firstRoot)
        advanceTimeBy(HIGHLIGHT_HOVER_DEBOUNCE_MILLIS + HIGHLIGHT_RENEWAL_MILLIS * 2)
        runCurrent()

        assertEquals(1, recorder.sent.size)
    }

    private class Recorder(private val answer: (HighlightNode) -> HighlightResult = { HighlightResult(shown = true) }) {
        val sent = mutableListOf<HighlightNode>()

        fun controller(scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)): NodeHighlightController = NodeHighlightController(
            scope = scope,
            send = { request ->
                sent += request
                answer(request)
            },
        )
    }
}
