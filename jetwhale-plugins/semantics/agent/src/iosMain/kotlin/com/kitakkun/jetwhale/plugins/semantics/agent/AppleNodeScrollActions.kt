package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.Foundation.NSIndexPath
import platform.UIKit.UIAccessibilityScrollDirection
import platform.UIKit.UIAccessibilityScrollDirectionDown
import platform.UIKit.UIAccessibilityScrollDirectionLeft
import platform.UIKit.UIAccessibilityScrollDirectionRight
import platform.UIKit.UIAccessibilityScrollDirectionUp
import platform.UIKit.UICollectionView
import platform.UIKit.UICollectionViewScrollPositionTop
import platform.UIKit.UIScrollView
import platform.UIKit.UITableView
import platform.UIKit.UITableViewScrollPosition
import platform.UIKit.UIView
import platform.UIKit.accessibilityFrame
import platform.UIKit.accessibilityScroll
import platform.UIKit.indexPathForItem
import platform.UIKit.indexPathForRow
import platform.darwin.NSObject
import kotlin.math.abs

/** Moving a scrolling view, or moving the scrolling views around a view so that it shows. */
@OptIn(ExperimentalForeignApi::class)
internal object AppleNodeScrollActions {
    /**
     * A `UIScrollView` moves by the distance asked for, within the range its content and insets
     * allow. Anything else is sent `accessibilityScroll`, which takes a direction and moves a page:
     * SwiftUI's and Compose's scrollables answer it, but neither honors a distance, so the result
     * says which direction was sent instead.
     */
    object ScrollBy : AppleNodeActionHandler {
        override val runsOnDisabledNode = true

        override fun isOfferedBy(node: NSObject) = node.isScrollable()

        override fun perform(node: NSObject, request: PerformNodeAction): NodeActionResult {
            if (node is UIScrollView) {
                val (x, y) = node.contentOffset.useContents { x to y }
                val (contentWidth, contentHeight) = node.contentSize.useContents { width to height }
                val (width, height) = node.bounds.useContents { size.width to size.height }
                // The insets extend the reachable range on both ends: a navigation bar's inset
                // puts the top of the content at a negative offset, and a bottom inset lets the
                // content scroll past its own end.
                val (insetTop, insetLeft, insetBottom, insetRight) = node.adjustedContentInset.useContents { listOf(top, left, bottom, right) }
                val targetX = (x + request.scrollX).coerceIn(-insetLeft, maxOf(-insetLeft, contentWidth - width + insetRight))
                val targetY = (y + request.scrollY).coerceIn(-insetTop, maxOf(-insetTop, contentHeight - height + insetBottom))
                node.setContentOffset(CGPointMake(targetX, targetY), animated = false)
                return NodeActionResult(performed = true)
            }
            val direction = scrollDirection(request.scrollX, request.scrollY)
                ?: return NodeActionResult.notSupported("ScrollBy needs a non-zero scrollX or scrollY")
            return NodeActionResult.performedIf(
                node.accessibilityScroll(direction),
                declined = "the node did not accept accessibilityScroll; only a UIScrollView scrolls by a distance",
            ).let { result ->
                if (result.performed) result.copy(message = "sent accessibilityScroll(${direction.name()}), which moves one page rather than the distance asked for") else result
            }
        }

        private fun scrollDirection(scrollX: Float, scrollY: Float): UIAccessibilityScrollDirection? = when {
            scrollX == 0f && scrollY == 0f -> null
            abs(scrollY) >= abs(scrollX) -> if (scrollY > 0f) UIAccessibilityScrollDirectionDown else UIAccessibilityScrollDirectionUp
            else -> if (scrollX > 0f) UIAccessibilityScrollDirectionRight else UIAccessibilityScrollDirectionLeft
        }

        private fun UIAccessibilityScrollDirection.name(): String = when (this) {
            UIAccessibilityScrollDirectionDown -> "down"
            UIAccessibilityScrollDirectionUp -> "up"
            UIAccessibilityScrollDirectionRight -> "right"
            UIAccessibilityScrollDirectionLeft -> "left"
            else -> toString()
        }
    }

    /**
     * The item-position scroll of the two list views UIKit offers; a SwiftUI `List` is a
     * `UICollectionView` underneath. The protocol's index counts items across the whole list, so it
     * is translated into the section-and-row pair the view API takes.
     */
    object ScrollToIndex : AppleNodeActionHandler {
        override val runsOnDisabledNode = true

        override fun isOfferedBy(node: NSObject) = node is UITableView || node is UICollectionView

        override fun perform(node: NSObject, request: PerformNodeAction): NodeActionResult {
            val index = request.index ?: return NodeActionResult.missingArgument(NodeAction.ScrollToIndex, "index")
            return when (node) {
                is UITableView -> {
                    val sections = (0 until node.numberOfSections).map { node.numberOfRowsInSection(it) }
                    val path = sectionedIndex(index, sections)
                        ?: return NodeActionResult.notSupported("index $index is out of bounds [0, ${sections.sum()})")
                    node.scrollToRowAtIndexPath(NSIndexPath.indexPathForRow(path.item, inSection = path.section), atScrollPosition = UITableViewScrollPosition.UITableViewScrollPositionTop, animated = false)
                    NodeActionResult(performed = true)
                }

                is UICollectionView -> {
                    val sections = (0 until node.numberOfSections).map { node.numberOfItemsInSection(it) }
                    val path = sectionedIndex(index, sections)
                        ?: return NodeActionResult.notSupported("index $index is out of bounds [0, ${sections.sum()})")
                    node.scrollToItemAtIndexPath(NSIndexPath.indexPathForItem(path.item, inSection = path.section), atScrollPosition = UICollectionViewScrollPositionTop, animated = false)
                    NodeActionResult(performed = true)
                }

                else -> NodeActionResult.notSupported("the node is not a UITableView or a UICollectionView")
            }
        }

        private class SectionedIndex(val section: Long, val item: Long)

        /** Where the [flat] index lands when the sections hold [sizes] items each, or `null` past the end. */
        private fun sectionedIndex(flat: Int, sizes: List<Long>): SectionedIndex? {
            if (flat < 0) return null
            var remaining = flat.toLong()
            sizes.forEachIndexed { section, size ->
                if (remaining < size) return SectionedIndex(section.toLong(), remaining)
                remaining -= size
            }
            return null
        }
    }

    /**
     * Every `UIScrollView` above a view is asked to show it, innermost first, the way
     * `scrollRectToVisible` is meant to be chained. A bare element has no view to climb from and
     * no way to say which container clips it, so it can only report whether it is already inside
     * the window.
     */
    object BringIntoView : AppleNodeActionHandler {
        override val runsOnDisabledNode = true

        override fun isOfferedBy(node: NSObject) = true

        override fun perform(node: NSObject, request: PerformNodeAction): NodeActionResult {
            val frame = node.accessibilityFrame.toNodeBounds()
            if (frame.isEmpty) return NodeActionResult.notSupported("the node has no size to bring into view")
            val view = node as? UIView
                ?: return if (node.isInsideItsWindow()) {
                    NodeActionResult(performed = true, message = "the node is already in view")
                } else {
                    NodeActionResult.notSupported("only a UIView can be scrolled into view on iOS; this node is a bare accessibility element")
                }

            val window = view.window ?: return NodeActionResult.notSupported("the view is not in a window")
            val scrollViews = view.scrollViewAncestors()
            // Inside the window is not enough: a scroll view between the view and the window clips
            // to its own bounds, so the view is in view only when every one of them shows all of it.
            val wholeViewShown = frame.intersect(window.frame.toNodeBounds()) == frame &&
                scrollViews.all { scrollView ->
                    val inScrollView = view.convertRect(view.bounds, toView = scrollView).toNodeBounds()
                    inScrollView.intersect(scrollView.bounds.toNodeBounds()) == inScrollView
                }
            if (wholeViewShown) return NodeActionResult(performed = true, message = "the view is already in view")
            if (scrollViews.isEmpty()) return NodeActionResult.notSupported("no UIScrollView above the view to scroll it into view")
            scrollViews.forEach { it.scrollRectToVisible(view.convertRect(view.bounds, toView = it), animated = false) }
            return NodeActionResult(performed = true)
        }

        private fun UIView.scrollViewAncestors(): List<UIScrollView> = buildList {
            var ancestor = superview
            while (ancestor != null) {
                if (ancestor is UIScrollView) add(ancestor)
                ancestor = ancestor.superview
            }
        }

        private fun NSObject.isInsideItsWindow(): Boolean {
            val window = AppleNodeIds.windowOf(this) ?: return false
            val frame = accessibilityFrame.toNodeBounds()
            return frame.intersect(window.frame.toNodeBounds()) == frame
        }
    }
}
