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

    @Test
    fun `dispatchDrag does not throw on empty scene`() = runBlocking {
        val scene = createTestScene()
        withContext(Dispatchers.Main) {
            dispatchDrag(scene, startX = 0f, startY = 100f, endX = 0f, endY = 500f, steps = 10)
        }
    }

    @Test
    fun `dispatchDrag sends events with steps = 1`() = runBlocking {
        val scene = createTestScene()
        withContext(Dispatchers.Main) {
            dispatchDrag(scene, startX = 0f, startY = 0f, endX = 100f, endY = 100f, steps = 1)
        }
    }

    @Test
    fun `dispatchDrag clamps steps to at least 1`() = runBlocking {
        val scene = createTestScene()
        withContext(Dispatchers.Main) {
            // steps = 0 should not throw (coerced to 1 internally)
            dispatchDrag(scene, startX = 0f, startY = 0f, endX = 200f, endY = 400f, steps = 0)
        }
    }

    @Test
    fun `dispatchDrag sends Press at start and Release at end`() = runBlocking {
        data class ReceivedEvent(val type: PointerEventType, val position: Offset)

        val receivedEvents = mutableListOf<ReceivedEvent>()
        val scene = createTestScene {
            Box(
                modifier = Modifier
                    .size(500.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                // Filter only Press/Move/Release to avoid Enter/Exit noise
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
            dispatchDrag(scene, startX = 10f, startY = 20f, endX = 300f, endY = 400f, steps = 3)
        }

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

        // Positions should be monotonically increasing toward the end
        for (i in 1 until dragPositions.size) {
            assertTrue(dragPositions[i].x >= dragPositions[i - 1].x, "Drag X should be non-decreasing at step $i")
            assertTrue(dragPositions[i].y >= dragPositions[i - 1].y, "Drag Y should be non-decreasing at step $i")
        }

        // Last position should be at or near the end
        assertEquals(400f, dragPositions.last().x, 0.01f, "Last drag position X should be at endX")
        assertEquals(400f, dragPositions.last().y, 0.01f, "Last drag position Y should be at endY")
    }
}
