package com.kitakkun.jetwhale.host.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val TrackWidth = 32.dp
private val TrackHeight = 18.dp
private val ThumbSize = 14.dp

/**
 * A compact toggle (32×18dp) for settings rows, where Material's 52×32 switch dwarfs the label.
 *
 * @param checked whether the switch is on.
 * @param onCheckedChange called with the new value when the switch is clicked.
 * @param contentDescription the switch's accessibility name — the label of the setting it
 * controls, since the switch shows none of its own.
 * @param enabled false greys the switch out and ignores clicks.
 */
@Composable
public fun JwSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> scheme.onSurface.copy(alpha = 0.12f)
            checked -> scheme.primary
            else -> scheme.outline
        },
        label = "switch-track",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) TrackWidth - ThumbSize - 2.dp else 2.dp,
        label = "switch-thumb",
    )
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(width = TrackWidth, height = TrackHeight)
            .jwFocusRing(interactionSource, CircleShape)
            .clip(CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .background(trackColor)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(ThumbSize)
                .background(if (enabled) scheme.surfaceContainerLowest else scheme.surface.copy(alpha = 0.6f), CircleShape),
        )
    }
}
