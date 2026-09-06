package com.kitakkun.jetwhale.host.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of a [JwProgressIndicator]. */
public object JwProgressIndicatorDefaults {
    /** The default diameter: fits inside a control beside its label. */
    public val size: Dp = 14.dp

    /** Stroke of the arc. */
    public val strokeWidth: Dp = 1.5f.dp

    /** The diameter of a spinner standing alone in an empty pane while its content loads. */
    public val largeSize: Dp = 28.dp
}

/**
 * An indeterminate spinner: a rotating arc, for "capturing…", "connecting…", anything whose end is
 * not known. Sized to sit before a button label or at the end of a status line.
 *
 * @param color the arc's color; defaults to the content color of the enclosing control.
 * @param size the diameter.
 * @param strokeWidth the arc's stroke.
 */
@Composable
public fun JwProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = LocalJwContentColor.current,
    size: Dp = JwProgressIndicatorDefaults.size,
    strokeWidth: Dp = JwProgressIndicatorDefaults.strokeWidth,
) {
    val transition = rememberInfiniteTransition(label = "jw-progress")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(ROTATION_PERIOD_MILLIS, easing = LinearEasing)),
        label = "jw-progress-rotation",
    )
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = ARC_SWEEP_DEGREES,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

private const val ROTATION_PERIOD_MILLIS = 1_000
private const val ARC_SWEEP_DEGREES = 270f
