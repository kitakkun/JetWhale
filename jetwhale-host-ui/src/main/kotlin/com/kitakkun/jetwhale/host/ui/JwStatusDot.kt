package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * An 8dp dot in the tone's color: connection state, liveness, "has something". [filled] false draws
 * a ring instead, for the weaker form of the same signal (available rather than active).
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
            .size(8.dp)
            .then(
                if (filled) {
                    Modifier.background(color, CircleShape)
                } else {
                    Modifier.border(1.5f.dp, color, CircleShape)
                },
            ),
    )
}
