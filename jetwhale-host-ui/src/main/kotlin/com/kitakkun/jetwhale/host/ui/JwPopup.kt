package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/** How a popup sits against the composable that opened it. */
public enum class JwPopupAnchor {
    /** Below the anchor, left edges aligned — a menu under its button. */
    BelowStart,

    /** Below the anchor, centered on it — a tooltip under its control. */
    BelowCenter,

    /** Beside the anchor's end edge, vertically centered — a tooltip off a rail icon. */
    EndCenter,
}

/**
 * Places a popup [gap] away from its anchor — below it, or beside it for [JwPopupAnchor.EndCenter] —
 * flipping to the other side when the window has no room there, and keeps it inside the window
 * along the other axis, [JwSpacing.small] clear of the window's edges where it fits, so a popup
 * opened at the edge of a pane does not sit flush against it. Shared by [JwTooltip] and [JwDropdownMenu]; use it with
 * `Popup` for a popup of your own that should sit the same way.
 *
 * @param anchor which edge of the anchor the popup aligns to.
 * @param gap the space between anchor and popup.
 */
@Composable
public fun rememberJwPopupPositionProvider(
    anchor: JwPopupAnchor,
    gap: Dp = JwSpacing.extraSmall,
): PopupPositionProvider {
    val density = LocalDensity.current
    val gapPx = with(density) { gap.roundToPx() }
    val edgeMarginPx = with(density) { JwSpacing.small.roundToPx() }
    return remember(key1 = anchor, key2 = gapPx, key3 = edgeMarginPx) { JwPopupPositionProvider(anchor = anchor, gapPx = gapPx, edgeMarginPx = edgeMarginPx) }
}

internal class JwPopupPositionProvider(
    private val anchor: JwPopupAnchor,
    private val gapPx: Int,
    private val edgeMarginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        if (anchor == JwPopupAnchor.EndCenter) {
            val ltr = layoutDirection == LayoutDirection.Ltr
            val after = if (ltr) anchorBounds.right + gapPx else anchorBounds.left - gapPx - popupContentSize.width
            val fitsAfter = after >= 0 && after + popupContentSize.width <= windowSize.width
            val x = if (fitsAfter) {
                after
            } else if (ltr) {
                (anchorBounds.left - gapPx - popupContentSize.width).coerceAtLeast(0)
            } else {
                (anchorBounds.right + gapPx).coerceAtMost((windowSize.width - popupContentSize.width).coerceAtLeast(0))
            }
            val y = (anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2)
                .insideWindow(popupContentSize.height, windowSize.height)
            return IntOffset(x, y)
        }
        val x = when (anchor) {
            JwPopupAnchor.BelowStart -> if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right - popupContentSize.width
            JwPopupAnchor.BelowCenter -> anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
            JwPopupAnchor.EndCenter -> error("handled above")
        }.insideWindow(popupContentSize.width, windowSize.width)
        val below = anchorBounds.bottom + gapPx
        val y = if (below + popupContentSize.height <= windowSize.height) {
            below
        } else {
            (anchorBounds.top - gapPx - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }

    /**
     * This start position moved inside a window of [windowExtent], [edgeMarginPx] clear of both
     * edges; a popup too large for the margins keeps only the window's own edges.
     */
    private fun Int.insideWindow(popupExtent: Int, windowExtent: Int): Int {
        val margin = if (popupExtent + 2 * edgeMarginPx <= windowExtent) edgeMarginPx else 0
        return coerceIn(margin, (windowExtent - popupExtent - margin).coerceAtLeast(margin))
    }
}
