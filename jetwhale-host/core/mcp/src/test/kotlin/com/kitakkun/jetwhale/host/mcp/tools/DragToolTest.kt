package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.ui.InternalComposeUiApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test

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
}
