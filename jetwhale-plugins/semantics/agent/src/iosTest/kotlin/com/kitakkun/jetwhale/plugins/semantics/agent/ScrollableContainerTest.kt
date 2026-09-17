package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UICoordinateSpaceProtocol
import platform.UIKit.UIFocusItemScrollableContainerProtocol
import platform.UIKit.UIView
import platform.darwin.NSObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A bare accessibility object that scrolls the way Compose's element does: through UIKit's focus-scrolling protocol. */
@OptIn(ExperimentalForeignApi::class)
private class ScrollingElement(private val contentHeight: Double) :
    NSObject(),
    UIFocusItemScrollableContainerProtocol {
    var offsetY = 0.0

    override fun visibleSize(): CValue<CGSize> = CGSizeMake(100.0, 100.0)

    override fun contentSize(): CValue<CGSize> = CGSizeMake(100.0, contentHeight)

    override fun contentOffset(): CValue<CGPoint> = CGPointMake(0.0, offsetY)

    override fun setContentOffset(contentOffset: CValue<CGPoint>) {
        offsetY = contentOffset.useContents { y }
    }

    override fun coordinateSpace(): UICoordinateSpaceProtocol = UIView()

    override fun focusItemsInRect(rect: CValue<CGRect>): List<*> = emptyList<Any>()
}

@OptIn(ExperimentalForeignApi::class)
class ScrollableContainerTest {
    private fun scrollBy(node: NSObject, dy: Float) = AppleNodeScrollActions.ScrollBy.perform(
        node,
        PerformNodeAction(rootId = "w", nodeId = -1, action = NodeAction.ScrollBy, scrollY = dy),
    )

    @Test
    fun `a container with content beyond its visible size is scrollable and advertises ScrollBy`() {
        val element = ScrollingElement(contentHeight = 300.0)

        assertTrue(element.isScrollable())
        assertTrue(AppleNodeScrollActions.ScrollBy.isOfferedBy(element))
    }

    @Test
    fun `a container whose content fits is not scrollable`() {
        assertFalse(ScrollingElement(contentHeight = 100.0).isScrollable())
    }

    @Test
    fun `a plain object is not a scrollable container`() {
        assertNull(NSObject().scrollableContainer())
        assertFalse(NSObject().isScrollable())
    }

    @Test
    fun `ScrollBy moves a container by the distance asked and clamps to its content`() {
        val element = ScrollingElement(contentHeight = 300.0)

        assertTrue(scrollBy(element, 50f).performed)
        assertEquals(50.0, element.offsetY)

        assertTrue(scrollBy(element, 500f).performed)
        assertEquals(200.0, element.offsetY)

        assertTrue(scrollBy(element, -500f).performed)
        assertEquals(0.0, element.offsetY)
    }
}
