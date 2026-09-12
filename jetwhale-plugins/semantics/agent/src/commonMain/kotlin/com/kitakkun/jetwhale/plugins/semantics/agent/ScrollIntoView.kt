package com.kitakkun.jetwhale.plugins.semantics.agent

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import kotlin.math.abs
import kotlin.math.sign

/**
 * Scrolls every scrollable ancestor of this node by the least amount that shows the whole node,
 * then asks [revealInHost] to do the same for whatever holds the composition.
 *
 * This is what Compose itself does for accessibility's "show on screen": walk up to each ancestor
 * with a `ScrollBy` action, compute how far the node sits outside that ancestor's viewport, scroll
 * by that, and carry the movement into the next ancestor's calculation. Each `ScrollBy` is applied
 * by the container on its next frame, so the node's bounds read the same until then — which is
 * why the movement is carried by hand rather than re-read.
 *
 * @param revealInHost given the bounds the node will have in root coordinates once the scrolls
 *   above have landed, scrolls whatever contains the composition — the Android `View`s around it —
 *   and says whether anything moved. A composition that fills its window has nothing outside it and
 *   answers `false`.
 */
internal fun SemanticsNode.scrollIntoView(revealInHost: (boundsInRoot: Rect) -> Boolean): NodeActionResult {
    var containersScrolled = 0
    var containersDeclined = 0
    var carried = Offset.Zero
    var ancestor = parent
    while (ancestor != null) {
        // A merged ancestor's config can carry a ScrollBy folded in from a scrollable descendant
        // that is not on this node's path; Compose reads the unmerged config here, which is
        // internal. A scrollable inside a merging container is rare enough to live with.
        val scrollBy = ancestor.config.getOrNull(SemanticsActions.ScrollBy)?.action
        if (scrollBy != null) {
            val delta = ancestor.alongItsAxes(
                scrollDeltaToReveal(
                    target = Rect(positionInRoot + carried, size.toSize()),
                    viewport = ancestor.viewportInRoot(),
                ),
            )
            if (delta != Offset.Zero) {
                val oriented = ancestor.inItsScrollDirection(delta)
                val handled = scrollBy(oriented.x, oriented.y)
                if (handled) containersScrolled++ else containersDeclined++
                carried -= delta
            }
        }
        ancestor = ancestor.parent
    }

    val hostScrolled = revealInHost(Rect(positionInRoot + carried, size.toSize()))

    return when {
        containersScrolled > 0 || hostScrolled -> NodeActionResult(
            performed = true,
            message = "scrolled ${containersScrolled + (if (hostScrolled) 1 else 0)} container(s); the scroll lands on the next frame, so capture the tree again to see the new bounds",
        )

        containersDeclined > 0 -> NodeActionResult(
            performed = false,
            message = "a scrollable ancestor declined to scroll (it may be at its end, or scrolling may be disabled)",
        )

        else -> NodeActionResult(performed = true, message = "the node is already in view")
    }
}

/**
 * Where a scrollable's viewport sits in root coordinates. The clipping that matters is the
 * scrollable's own — its size in its direct parent — not what any further ancestor clips away, so
 * the bounds in the parent are taken and only translated to root.
 */
private fun SemanticsNode.viewportInRoot(): Rect {
    val coordinates = layoutInfo.coordinates
    val parentInRoot = coordinates.parentLayoutCoordinates?.positionInRoot() ?: Offset.Zero
    return coordinates.boundsInParent().translate(parentInRoot)
}

/**
 * How far [target] has to move, in root coordinates, to end up inside [viewport]: the smaller of
 * the two moves that would align either edge, and nothing on an axis where the target already fits
 * or overhangs the viewport on both sides.
 */
internal fun scrollDeltaToReveal(target: Rect, viewport: Rect): Offset = Offset(
    x = leastAligningDelta(target.left - viewport.left, target.right - viewport.right),
    y = leastAligningDelta(target.top - viewport.top, target.bottom - viewport.bottom),
)

private fun leastAligningDelta(alignStart: Float, alignEnd: Float): Float = when {
    sign(alignStart) != sign(alignEnd) -> 0f
    abs(alignStart) < abs(alignEnd) -> alignStart
    else -> alignEnd
}

/** [delta] with the axes this container cannot scroll along zeroed: a `LazyRow` is not asked to move down. */
private fun SemanticsNode.alongItsAxes(delta: Offset): Offset = Offset(
    x = if (config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) == null) 0f else delta.x,
    y = if (config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) == null) 0f else delta.y,
)

/**
 * `ScrollBy` counts in the container's own direction: a `reverseScrolling` container and a
 * right-to-left layout both flip the sign of a move meant in root coordinates.
 */
private fun SemanticsNode.inItsScrollDirection(delta: Offset): Offset {
    var x = delta.x
    var y = delta.y
    if (config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.reverseScrolling == true) x = -x
    if (layoutInfo.layoutDirection == LayoutDirection.Rtl) x = -x
    if (config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.reverseScrolling == true) y = -y
    return Offset(x, y)
}
