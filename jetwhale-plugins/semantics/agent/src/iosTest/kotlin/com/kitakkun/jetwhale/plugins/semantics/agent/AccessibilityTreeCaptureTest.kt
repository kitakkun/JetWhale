package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.AppleNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.accessibilityFrame
import platform.UIKit.accessibilityViewIsModal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalForeignApi::class)
class AccessibilityTreeCaptureTest {
    // A window that is not on a screen is hidden and answers accessibilityFrame with an empty
    // rectangle, so both are set outright — the walk's visibility rule is what is under test.
    private val window = UIWindow(frame = CGRectMake(0.0, 0.0, 400.0, 800.0)).apply {
        hidden = false
        accessibilityFrame = frame
    }

    private fun view(x: Double, y: Double): UIView = UIView(frame = CGRectMake(x, y, 100.0, 100.0)).apply {
        accessibilityFrame = frame
    }

    private fun capture(): AppleNode = assertNotNull(
        AppleNodeIds.trackingCapture(window) { window.toAppleNode(NodeTreeCaptureOptions(includeInvisible = true), window, depth = 0) },
    )

    @Test
    fun `a view behind a modal sibling reads as invisible`() {
        val behind = view(0.0, 0.0)
        val modal = view(0.0, 200.0).apply { accessibilityViewIsModal = true }
        window.addSubview(behind)
        window.addSubview(modal)

        val children = capture().children.map { it as AppleNode }

        assertEquals(listOf(false, true), children.map { it.isVisible })
    }

    @Test
    fun `siblings without a modal among them are all visible`() {
        window.addSubview(view(0.0, 0.0))
        window.addSubview(view(0.0, 200.0))

        val children = capture().children

        assertEquals(listOf(true, true), children.map { it.isVisible })
    }

    @Test
    fun `a hidden view is invisible and so is everything under it`() {
        val hidden = view(0.0, 0.0).apply { hidden = true }
        hidden.addSubview(view(0.0, 0.0))
        window.addSubview(hidden)

        val node = capture().children.single()

        assertEquals(false, node.isVisible)
        assertEquals(false, node.children.single().isVisible)
    }
}
