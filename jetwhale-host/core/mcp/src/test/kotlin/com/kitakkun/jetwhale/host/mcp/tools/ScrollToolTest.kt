package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.ui.InternalComposeUiApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test

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
}
