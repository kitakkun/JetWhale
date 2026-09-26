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
import platform.UIKit.UICollectionViewFlowLayout
import platform.UIKit.UICollectionViewScrollDirection
import platform.UIKit.UICollectionViewScrollPositionLeft
import platform.UIKit.UICollectionViewScrollPositionTop
import platform.UIKit.UIFocusItemScrollableContainerProtocol
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
     * allow; so does a focus-scrolling container, which is what a Compose scrollable is (see
     * [scrollableContainer]). Those two are what the action is advertised for. A caller may still
     * name it on any other node — the protocol gives no side-effect-free way to tell that a bare
     * element scrolls — and that node is sent `accessibilityScroll`, which takes a direction and
     * moves a page, so the result says which direction was sent instead.
     */
    object ScrollBy : AppleNodeActionHandler {
        override val runsOnDisabledNode = true

        override fun isOfferedBy(node: NSObject) = node.isScrollable()

        override fun perform(node: NSObject, request: PerformNodeAction): NodeActionResult {
            if (node is UIScrollView) {
                if (!node.isScrollable()) return NodeActionResult.notSupported("the scroll view has nothing to scroll")
                return node.scrollBy(request)
            }
            val container = node.scrollableContainer()
            if (container != null) {
                if (!node.isScrollable()) return NodeActionResult.notSupported("the container has nothing to scroll")
                return container.scrollBy(request)
            }
            val direction = scrollDirection(request.scrollX, request.scrollY)
                ?: return NodeActionResult.notSupported("ScrollBy needs a non-zero scrollX or scrollY")
            return NodeActionResult.performedIf(
                node.accessibilityScroll(direction),
                declined = "the node did not accept accessibilityScroll; " +
                    "only a scroll view or a scrollable container scrolls by a distance",
            ).let { result ->
                if (result.performed) {
                    result.copy(
                        message = "sent accessibilityScroll(${direction.name()}), which moves one page rather than the distance asked for",
                    )
                } else {
                    result
                }
            }
        }

        private fun UIScrollView.scrollBy(request: PerformNodeAction): NodeActionResult {
            val (x, y) = contentOffset.useContents { x to y }
            val (contentWidth, contentHeight) = contentSize.useContents { width to height }
            val (width, height) = bounds.useContents { size.width to size.height }
            val (xRange, yRange) = adjustedContentInset.useContents {
                reachableRange(content = contentWidth, viewport = width, leadingInset = left, trailingInset = right) to
                    reachableRange(content = contentHeight, viewport = height, leadingInset = top, trailingInset = bottom)
            }
            setContentOffset(CGPointMake((x + request.scrollX).coerceIn(xRange), (y + request.scrollY).coerceIn(yRange)), animated = false)
            return NodeActionResult(performed = true)
        }

        private fun UIFocusItemScrollableContainerProtocol.scrollBy(request: PerformNodeAction): NodeActionResult {
            val (x, y) = contentOffset.useContents { x to y }
            val (contentWidth, contentHeight) = contentSize.useContents { width to height }
            val (width, height) = visibleSize.useContents { width to height }
            val targetX = (x + request.scrollX).coerceIn(0.0, maxOf(0.0, contentWidth - width))
            val targetY = (y + request.scrollY).coerceIn(0.0, maxOf(0.0, contentHeight - height))
            setContentOffset(CGPointMake(targetX, targetY))
            return NodeActionResult(performed = true)
        }

        /**
         * The offsets one axis of a scroll view can be set to. The insets extend the reachable range
         * on both ends: a navigation bar's inset puts the top of the content at a negative offset,
         * and a bottom inset lets the content scroll past its own end.
         */
        private fun reachableRange(
            content: Double,
            viewport: Double,
            leadingInset: Double,
            trailingInset: Double,
        ): ClosedFloatingPointRange<Double> = -leadingInset..maxOf(-leadingInset, content - viewport + trailingInset)

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
                    val sections = (0 until node.numberOfSections).map(node::numberOfRowsInSection)
                    val path = sectionedIndex(index, sections)
                        ?: return NodeActionResult.notSupported("index $index is out of bounds [0, ${sections.sum()})")
                    node.scrollToRowAtIndexPath(
                        NSIndexPath.indexPathForRow(path.item, inSection = path.section),
                        atScrollPosition = UITableViewScrollPosition.UITableViewScrollPositionTop,
                        animated = false,
                    )
                    NodeActionResult(performed = true)
                }

                is UICollectionView -> {
                    val sections = (0 until node.numberOfSections).map(node::numberOfItemsInSection)
                    val path = sectionedIndex(index, sections)
                        ?: return NodeActionResult.notSupported("index $index is out of bounds [0, ${sections.sum()})")
                    node.scrollToItemAtIndexPath(
                        NSIndexPath.indexPathForItem(path.item, inSection = path.section),
                        atScrollPosition = node.startPosition(),
                        animated = false,
                    )
                    NodeActionResult(performed = true)
                }

                else -> NodeActionResult.notSupported("the node is not a UITableView or a UICollectionView")
            }
        }

        /**
         * "At its start" is the top for a vertical list and the leading edge for a horizontal one.
         * A flow layout says which way it scrolls; any other layout is read from where its content
         * overflows.
         */
        private fun UICollectionView.startPosition(): ULong {
            val flowDirection = (collectionViewLayout as? UICollectionViewFlowLayout)?.scrollDirection
            val horizontal = when (flowDirection) {
                UICollectionViewScrollDirection.UICollectionViewScrollDirectionHorizontal -> true

                UICollectionViewScrollDirection.UICollectionViewScrollDirectionVertical -> false

                else -> {
                    val (contentWidth, contentHeight) = contentSize.useContents { width to height }
                    val (width, height) = bounds.useContents { size.width to size.height }
                    contentWidth > width && contentHeight <= height
                }
            }
            return if (horizontal) UICollectionViewScrollPositionLeft else UICollectionViewScrollPositionTop
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
                    NodeActionResult.notSupported(
                        "only a UIView can be scrolled into view on iOS; this node is a bare accessibility element",
                    )
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
