package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of a [JwStatusDot]. */
public object JwStatusDotDefaults {
    /** Diameter of the dot. */
    public val size: Dp = 8.dp

    /** Stroke of the ring drawn when the dot is not filled. */
    public val ringWidth: Dp = 1.5f.dp
}

/**
 * A dot of [JwStatusDotDefaults.size] in the tone's color: connection state, liveness, "has something".
 *
 * @param tone the color family.
 * @param filled `false` draws a ring instead of a disc, for the weaker form of the same signal
 * (available rather than active).
 */
@Composable
public fun JwStatusDot(
    tone: JwTone,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
) {
    val color = tone.color
    Box(
        modifier = modifier
            .size(JwStatusDotDefaults.size)
            .then(
                if (filled) {
                    Modifier.background(color, CircleShape)
                } else {
                    Modifier.border(JwStatusDotDefaults.ringWidth, color, CircleShape)
                },
            ),
    )
}
