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
}

/**
 * Places a popup [gap] below its anchor, flipping above when the window has no room below, and
 * keeps it inside the window sideways. Shared by [JwTooltip] and [JwDropdownMenu]; use it with
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
    val gapPx = with(LocalDensity.current) { gap.roundToPx() }
    return remember(anchor, gapPx) { JwPopupPositionProvider(anchor, gapPx) }
}

private class JwPopupPositionProvider(
    private val anchor: JwPopupAnchor,
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = when (anchor) {
            JwPopupAnchor.BelowStart -> if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right - popupContentSize.width
            JwPopupAnchor.BelowCenter -> anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        }.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gapPx
        val y = if (below + popupContentSize.height <= windowSize.height) {
            below
        } else {
            (anchorBounds.top - gapPx - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}
