package com.kitakkun.jetwhale.plugins.semantics.agent

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals

class ScrollDeltaToRevealTest {
    private val viewport = Rect(left = 0f, top = 100f, right = 400f, bottom = 500f)

    @Test
    fun `a target inside the viewport needs no scroll`() {
        assertEquals(Offset.Zero, ScrollActions.BringIntoView.scrollDeltaToReveal(target = Rect(10f, 150f, 200f, 300f), viewport = viewport))
    }

    @Test
    fun `a target below the viewport scrolls down just enough to show its bottom edge`() {
        assertEquals(Offset(0f, 200f), ScrollActions.BringIntoView.scrollDeltaToReveal(target = Rect(10f, 600f, 200f, 700f), viewport = viewport))
    }

    @Test
    fun `a target above the viewport scrolls up just enough to show its top edge`() {
        assertEquals(Offset(0f, -80f), ScrollActions.BringIntoView.scrollDeltaToReveal(target = Rect(10f, 20f, 200f, 120f), viewport = viewport))
    }

    @Test
    fun `a target to the right scrolls sideways and not vertically`() {
        assertEquals(Offset(150f, 0f), ScrollActions.BringIntoView.scrollDeltaToReveal(target = Rect(450f, 150f, 550f, 300f), viewport = viewport))
    }

    @Test
    fun `a target taller than the viewport that overlaps it is left where it is`() {
        assertEquals(Offset.Zero, ScrollActions.BringIntoView.scrollDeltaToReveal(target = Rect(10f, 50f, 200f, 800f), viewport = viewport))
    }

    @Test
    fun `a target partly past the bottom edge scrolls by the overhang only`() {
        assertEquals(Offset(0f, 50f), ScrollActions.BringIntoView.scrollDeltaToReveal(target = Rect(10f, 400f, 200f, 550f), viewport = viewport))
    }
}
