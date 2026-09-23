package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.AppleNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UITextField
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.accessibilityFrame
import platform.UIKit.accessibilityViewIsModal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class AccessibilityTreeCaptureTest {
    // A window that is not on a screen is hidden and answers accessibilityFrame with an empty
    // rectangle, so both are set outright — the walk's visibility rule is what is under test.
    private val window = UIWindow(frame = CGRectMake(x = 0.0, y = 0.0, width = 400.0, height = 800.0)).apply {
        hidden = false
        accessibilityFrame = frame
    }

    @Test
    fun `a view behind a modal sibling reads as invisible`() {
        val behind = view(0.0, 0.0)
        val modal = view(0.0, 200.0).apply { accessibilityViewIsModal = true }
        window.addSubview(behind)
        window.addSubview(modal)

        val children = capture().children.map { it as AppleNode }

        assertEquals(listOf(false, true), children.map(AppleNode::isVisible))
    }

    private fun view(x: Double, y: Double): UIView = UIView(frame = CGRectMake(x = x, y = y, width = 100.0, height = 100.0)).apply {
        accessibilityFrame = frame
    }

    private fun capture(): AppleNode = assertNotNull(
        AppleNodeIds.trackingCapture(window) { window.toAppleNode(NodeTreeCaptureOptions(includeInvisible = true), window, depth = 0) },
    )

    @Test
    fun `a secure text field keeps its text to itself`() {
        val password = UITextField(frame = CGRectMake(x = 0.0, y = 0.0, width = 200.0, height = 40.0)).apply {
            accessibilityFrame = frame
            text = "hunter2"
            secureTextEntry = true
        }
        val plain = UITextField(frame = CGRectMake(x = 0.0, y = 100.0, width = 200.0, height = 40.0)).apply {
            accessibilityFrame = frame
            text = "visible"
        }
        window.addSubview(password)
        window.addSubview(plain)

        val (secureNode, plainNode) = capture().children.map { it as AppleNode }

        assertTrue(secureNode.isEditable)
        assertNull(secureNode.editableText)
        assertEquals("visible", plainNode.editableText)
    }

    @Test
    fun `siblings without a modal among them are all visible`() {
        window.addSubview(view(0.0, 0.0))
        window.addSubview(view(0.0, 200.0))

        val children = capture().children

        assertEquals(listOf(true, true), children.map(UiNode::isVisible))
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
