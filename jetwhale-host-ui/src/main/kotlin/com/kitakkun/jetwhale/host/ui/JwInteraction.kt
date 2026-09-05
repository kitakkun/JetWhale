package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Draws the accent focus ring every Jw control shows while it holds keyboard focus. The controls
 * suppress the Material ripple, so this is the only sign of focus a keyboard user gets; apply it
 * to a custom control built on the same [InteractionSource] as its `clickable`.
 *
 * @param interactionSource the control's interaction source; the ring follows its focus state.
 * @param shape the control's shape, so the ring hugs its corners.
 */
@Composable
public fun Modifier.jwFocusRing(interactionSource: InteractionSource, shape: Shape): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    return if (focused) border(1.5f.dp, MaterialTheme.colorScheme.primary, shape) else this
}
