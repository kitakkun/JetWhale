package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
class DragToolTest {

    // A scene with no content has nothing to observe; the point is only that the drag completes.
    @Suppress("KOTRAIL_TEST_WITHOUT_ASSERTION")
    @Test
    fun `dispatchDrag does not throw on empty scene`() = runBlocking {
        val scene = createTestScene()
        withContext(Dispatchers.Main) {
            dispatchDrag(scene, startX = 0f, startY = 100f, endX = 0f, endY = 500f, steps = 10)
        }
    }

    @Test
    fun `dispatchDrag with one step presses at the start and releases at the end`() = runBlocking {
        val events = dragAndRecord(start = Offset(0f, 0f), end = Offset(100f, 100f), steps = 1)

        // The one Move lands where the Release does, and Compose may fold it into the Release.
        assertEquals(
            listOf(PointerEventType.Press to Offset(0f, 0f), PointerEventType.Release to Offset(100f, 100f)),
            events.filter { it.type != PointerEventType.Move }.map { it.type to it.position },
        )
    }

    @Test
    fun `dispatchDrag with zero steps still ends the drag at the end position`() = runBlocking {
        val events = dragAndRecord(start = Offset(0f, 0f), end = Offset(200f, 400f), steps = 0)

        assertEquals(
            listOf(PointerEventType.Press to Offset(0f, 0f), PointerEventType.Release to Offset(200f, 400f)),
            events.filter { it.type != PointerEventType.Move }.map { it.type to it.position },
        )
    }

    @Test
    fun `dispatchDrag sends Press at start and Release at end`() = runBlocking {
        val receivedEvents = dragAndRecord(start = Offset(10f, 20f), end = Offset(300f, 400f), steps = 3)

        val pressEvents = receivedEvents.filter { it.type == PointerEventType.Press }
        val releaseEvents = receivedEvents.filter { it.type == PointerEventType.Release }
        val moveEvents = receivedEvents.filter { it.type == PointerEventType.Move }

        assertEquals(1, pressEvents.size, "Expected exactly 1 Press event")
        assertEquals(Offset(10f, 20f), pressEvents.first().position, "Press should be at start position")

        assertEquals(1, releaseEvents.size, "Expected exactly 1 Release event")
        assertEquals(Offset(300f, 400f), releaseEvents.last().position, "Release should be at end position")

        assertTrue(moveEvents.isNotEmpty(), "Expected at least 1 Move event between Press and Release")
    }

    @Test
    fun `dispatchDrag move events interpolate positions correctly`() = runBlocking {
        val dragPositions = mutableListOf<Offset>()
        var dragEnded = false

        val scene = createTestScene {
            Box(
                modifier = Modifier
                    .size(500.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, _ -> dragPositions += change.position },
                            onDragEnd = { dragEnded = true },
                        )
                    },
            )
        }
        renderTestScene(scene)

        val steps = 4
        withContext(Dispatchers.Main) {
            dispatchDrag(scene, startX = 0f, startY = 0f, endX = 400f, endY = 400f, steps = steps)
        }

        assertTrue(dragEnded, "Drag should have ended cleanly via onDragEnd")

        // The last move is at fraction=1.0 (same position as release); Compose may coalesce it,
        // so we accept either `steps` or `steps - 1` drag events.
        assertTrue(
            dragPositions.size >= steps - 1,
            "Expected at least ${steps - 1} drag events, got ${dragPositions.size}",
        )

        for (i in 1 until dragPositions.size) {
            assertTrue(dragPositions[i].x >= dragPositions[i - 1].x, "Drag X should be non-decreasing at step $i")
            assertTrue(dragPositions[i].y >= dragPositions[i - 1].y, "Drag Y should be non-decreasing at step $i")
        }

        assertEquals(expected = 400f, actual = dragPositions.last().x, absoluteTolerance = 0.01f, message = "Last drag position X should be at endX")
        assertEquals(expected = 400f, actual = dragPositions.last().y, absoluteTolerance = 0.01f, message = "Last drag position Y should be at endY")
    }

    /** Drags across a box that records every press, move and release it receives. */
    private suspend fun dragAndRecord(start: Offset, end: Offset, steps: Int): List<ReceivedEvent> {
        val receivedEvents = mutableListOf<ReceivedEvent>()
        val scene = createTestScene {
            Box(
                modifier = Modifier
                    .size(500.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type !in listOf(PointerEventType.Press, PointerEventType.Move, PointerEventType.Release)) continue
                                val pos = event.changes.firstOrNull()?.position ?: continue
                                receivedEvents += ReceivedEvent(event.type, pos)
                            }
                        }
                    },
            )
        }
        renderTestScene(scene)
        withContext(Dispatchers.Main) {
            dispatchDrag(scene, startX = start.x, startY = start.y, endX = end.x, endY = end.y, steps = steps)
        }
        return receivedEvents
    }
}

private data class ReceivedEvent(val type: PointerEventType, val position: Offset)
