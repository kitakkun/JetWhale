package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class ScrollToolTest {

    @Test
    fun `dispatchScroll does not throw on empty scene`() = runBlocking {
        val scene = createTestScene()
        withContext(Dispatchers.Main) {
            dispatchScroll(scene, x = 100f, y = 200f, deltaX = 0f, deltaY = 50f)
        }
    }

    @Test
    fun `dispatchScroll does not throw with negative delta (scroll up)`() = runBlocking {
        val scene = createTestScene()
        withContext(Dispatchers.Main) {
            dispatchScroll(scene, x = 100f, y = 200f, deltaX = 0f, deltaY = -50f)
        }
    }

    @Test
    fun `dispatchScroll does not throw with horizontal delta`() = runBlocking {
        val scene = createTestScene()
        withContext(Dispatchers.Main) {
            dispatchScroll(scene, x = 100f, y = 200f, deltaX = 30f, deltaY = 0f)
        }
    }

    @Test
    fun `dispatchScroll advances vertical scroll position`() = runBlocking {
        var scrollState: ScrollState? = null
        val scene = createTestScene {
            val state = rememberScrollState()
            scrollState = state
            Column(
                modifier = Modifier
                    .size(200.dp)
                    .verticalScroll(state),
            ) {
                repeat(20) { Box(modifier = Modifier.size(50.dp)) }
            }
        }
        renderTestScene(scene)

        withContext(Dispatchers.Main) {
            dispatchScroll(scene, x = 100f, y = 100f, deltaX = 0f, deltaY = 50f)
        }

        val state = checkNotNull(scrollState) { "ScrollState was not captured" }
        assertTrue(state.value > 0, "Expected scroll position to have advanced, but was ${state.value}")
    }

    @Test
    fun `dispatchScroll dispatches scroll events with correct delta direction`() = runBlocking {
        val receivedDeltas = mutableListOf<Offset>()
        val scene = createTestScene {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) {
                                    event.changes.firstOrNull()?.scrollDelta?.let { receivedDeltas += it }
                                }
                            }
                        }
                    },
            )
        }
        renderTestScene(scene)

        withContext(Dispatchers.Main) {
            dispatchScroll(scene, x = 100f, y = 100f, deltaX = 0f, deltaY = 50f)
        }
        withContext(Dispatchers.Main) {
            dispatchScroll(scene, x = 100f, y = 100f, deltaX = 0f, deltaY = -30f)
        }

        assertEquals(2, receivedDeltas.size, "Expected 2 scroll events to be received")
        assertTrue(receivedDeltas[0].y > 0, "First (down) scroll should have positive deltaY, but was ${receivedDeltas[0].y}")
        assertTrue(receivedDeltas[1].y < 0, "Second (up) scroll should have negative deltaY, but was ${receivedDeltas[1].y}")
    }
}
