package com.kitakkun.jetwhale.host.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A 16dp checkbox with its [label] beside it; the whole row toggles. Sized for toolbars and option
 * rows, where Material's 48dp touch target would push everything else apart.
 *
 * @param checked whether the box is ticked.
 * @param onCheckedChange called with the new value when the row is clicked.
 * @param label the text beside the box, part of the click target; null for a bare box in a table
 * column, which the surrounding row must then name.
 * @param enabled false greys the row out and ignores clicks.
 */
@Composable
public fun JwCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = JwTheme.colors
    val boxColor by animateColorAsState(
        targetValue = when {
            !enabled -> scheme.onSurface.copy(alpha = 0.12f)
            checked -> scheme.primary
            else -> Color.Transparent
        },
        label = "checkbox-box",
    )
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(JwMetrics.controlHeight)
            .clip(MaterialTheme.shapes.small)
            .jwFocusRing(interactionSource, MaterialTheme.shapes.small)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = JwSpacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(boxColor, MaterialTheme.shapes.extraSmall)
                .border(
                    JwMetrics.borderWidth,
                    if (checked && enabled) scheme.primary else scheme.outline,
                    MaterialTheme.shapes.extraSmall,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                JwIcon(
                    imageVector = JwIcons.Check,
                    contentDescription = null,
                    tint = if (enabled) scheme.onPrimary else colors.textDisabled,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) scheme.onSurface else colors.textDisabled,
                maxLines = 1,
            )
        }
    }
}
