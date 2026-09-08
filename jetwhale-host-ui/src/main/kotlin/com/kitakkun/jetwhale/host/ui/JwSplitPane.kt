package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import kotlin.math.roundToInt

/** Sizes of a [JwSplitPane]. */
public object JwSplitPaneDefaults {
    /** The smallest either pane is dragged down to. */
    public val minPaneSize: Dp = 120.dp

    /** Width of the invisible grab area laid over the hairline divider. */
    public val handleSize: Dp = 8.dp
}

/**
 * Where the divider of a [JwSplitPane] sits, as the fraction of the available length given to the
 * first pane. Hoist it to persist the position: read [fraction] into storage and construct the
 * state from the stored value.
 *
 * @param initialFraction the divider's starting position, 0..1.
 */
@Stable
public class JwSplitPaneState(initialFraction: Float) {
    /** The fraction of the length the first pane takes, 0..1; the panes' minimum sizes clamp it. */
    public var fraction: Float by mutableFloatStateOf(initialFraction.coerceIn(0f, 1f))
}

/**
 * A [JwSplitPaneState] kept across recompositions.
 *
 * @param initialFraction the divider's starting position, 0..1.
 */
@Composable
public fun rememberJwSplitPaneState(initialFraction: Float): JwSplitPaneState = remember { JwSplitPaneState(initialFraction) }

/**
 * Two panes side by side (or one above the other) with a draggable hairline between them: the
 * list-and-detail layout of an inspector. Each pane fills its side; put its own scrolling inside.
 *
 * @param first the leading (left or top) pane.
 * @param second the trailing (right or bottom) pane.
 * @param orientation [Orientation.Horizontal] places the panes side by side.
 * @param state where the divider is; hoist it to persist the position.
 * @param firstMinSize the smallest the first pane is dragged down to.
 * @param secondMinSize the smallest the second pane is dragged down to.
 */
@Composable
public fun JwSplitPane(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    orientation: Orientation = Orientation.Horizontal,
    state: JwSplitPaneState = rememberJwSplitPaneState(0.5f),
    firstMinSize: Dp = JwSplitPaneDefaults.minPaneSize,
    secondMinSize: Dp = JwSplitPaneDefaults.minPaneSize,
) {
    val density = LocalDensity.current
    val horizontal = orientation == Orientation.Horizontal
    var length by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta ->
        if (length > 0f) state.fraction = (state.fraction + delta / length).coerceIn(0f, 1f)
    }
    val cursor = if (horizontal) Cursor.E_RESIZE_CURSOR else Cursor.N_RESIZE_CURSOR
    Layout(
        modifier = modifier,
        content = {
            Box(modifier = Modifier.then(if (horizontal) Modifier.fillMaxHeight() else Modifier.fillMaxWidth())) { first() }
            Box(modifier = Modifier.then(if (horizontal) Modifier.fillMaxHeight() else Modifier.fillMaxWidth())) { second() }
            if (horizontal) JwVerticalDivider() else JwHorizontalDivider()
            // A wider, invisible hit area over the divider, so it stays comfortable to grab.
            Box(
                modifier = Modifier
                    .then(if (horizontal) Modifier.width(JwSplitPaneDefaults.handleSize).fillMaxHeight() else Modifier.height(JwSplitPaneDefaults.handleSize).fillMaxWidth())
                    .pointerHoverIcon(PointerIcon(Cursor(cursor)))
                    .draggable(state = dragState, orientation = orientation),
            )
        },
    ) { measurables, constraints ->
        val total = if (horizontal) constraints.maxWidth else constraints.maxHeight
        val cross = if (horizontal) constraints.maxHeight else constraints.maxWidth
        length = total.toFloat()
        val dividerPx = with(density) { JwMetrics.borderWidth.roundToPx() }
        val handlePx = with(density) { JwSplitPaneDefaults.handleSize.roundToPx() }
        val firstMin = with(density) { firstMinSize.roundToPx() }
        val secondMin = with(density) { secondMinSize.roundToPx() }
        val available = (total - dividerPx).coerceAtLeast(0)
        val firstLength = (available * state.fraction).roundToInt()
            .coerceIn(firstMin.coerceAtMost(available), (available - secondMin).coerceAtLeast(firstMin.coerceAtMost(available)))
        val secondLength = (available - firstLength).coerceAtLeast(0)
        val (firstMeasurable, secondMeasurable, dividerMeasurable, handleMeasurable) = measurables
        fun paneConstraints(l: Int) = if (horizontal) {
            constraints.copy(minWidth = l, maxWidth = l, minHeight = cross, maxHeight = cross)
        } else {
            constraints.copy(minHeight = l, maxHeight = l, minWidth = cross, maxWidth = cross)
        }
        val firstPlaceable = firstMeasurable.measure(paneConstraints(firstLength))
        val secondPlaceable = secondMeasurable.measure(paneConstraints(secondLength))
        val dividerPlaceable = dividerMeasurable.measure(paneConstraints(dividerPx))
        val handlePlaceable = handleMeasurable.measure(paneConstraints(handlePx))
        val width = if (horizontal) total else cross
        val height = if (horizontal) cross else total
        layout(width, height) {
            if (horizontal) {
                firstPlaceable.placeRelative(0, 0)
                dividerPlaceable.placeRelative(firstLength, 0)
                secondPlaceable.placeRelative(firstLength + dividerPx, 0)
                handlePlaceable.placeRelative(firstLength + dividerPx / 2 - handlePx / 2, 0)
            } else {
                firstPlaceable.placeRelative(0, 0)
                dividerPlaceable.placeRelative(0, firstLength)
                secondPlaceable.placeRelative(0, firstLength + dividerPx)
                handlePlaceable.placeRelative(0, firstLength + dividerPx / 2 - handlePx / 2)
            }
        }
    }
}
