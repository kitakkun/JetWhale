package com.kitakkun.jetwhale.host.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class JwPopupPositionProviderTest {
    private val provider = JwPopupPositionProvider(JwPopupAnchor.BelowStart, gapPx = 4, edgeMarginPx = 6)

    @Test
    fun `a menu opens below its anchor with the gap between them`() {
        val offset = provider.calculatePosition(IntRect(left = 100, top = 10, right = 200, bottom = 40), IntSize(800, 600), LayoutDirection.Ltr, IntSize(160, 100))

        assertEquals(IntOffset(100, 44), offset)
    }

    @Test
    fun `a menu whose anchor touches the window edge is kept clear of it`() {
        val offset = provider.calculatePosition(IntRect(left = 0, top = 10, right = 100, bottom = 40), IntSize(800, 600), LayoutDirection.Ltr, IntSize(160, 100))

        assertEquals(6, offset.x)
    }

    @Test
    fun `a menu that would pass the far edge stops short of it`() {
        val offset = provider.calculatePosition(IntRect(left = 700, top = 10, right = 790, bottom = 40), IntSize(800, 600), LayoutDirection.Ltr, IntSize(160, 100))

        assertEquals(800 - 160 - 6, offset.x)
    }

    @Test
    fun `a menu too wide for the margins still fits the window`() {
        val offset = provider.calculatePosition(IntRect(left = 0, top = 10, right = 100, bottom = 40), IntSize(165, 600), LayoutDirection.Ltr, IntSize(160, 100))

        assertEquals(0, offset.x)
    }
}
