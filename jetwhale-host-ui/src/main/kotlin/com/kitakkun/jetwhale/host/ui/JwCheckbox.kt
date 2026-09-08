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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of a [JwCheckbox]. */
public object JwCheckboxDefaults {
    /** Side of the box. */
    public val size: Dp = 16.dp
}

/** Side of the check mark inside the box. */
private val CheckMarkSize = 12.dp

/** The dash inside an indeterminate box. */
private val IndeterminateMarkWidth = 8.dp
private val IndeterminateMarkHeight = 2.dp

/**
 * A [JwCheckboxDefaults.size] checkbox with its [label] beside it; the whole row toggles. Sized for toolbars and option
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
    val interactionSource = remember { MutableInteractionSource() }
    CheckboxRow(
        state = ToggleableState(checked),
        label = label,
        enabled = enabled,
        modifier = modifier
            .jwFocusRing(interactionSource, JwShapes.small)
            .clip(JwShapes.small)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange,
            ),
    )
}

/**
 * A [JwCheckbox] that can also be half-ticked: the parent of a group whose children are partly
 * selected. It summarizes rather than stores, so a click reports through [onClick] and the caller
 * decides what a mixed selection turns into.
 *
 * @param state on, off, or indeterminate for a partial selection.
 * @param onClick called when the row is clicked.
 * @param label the text beside the box, part of the click target; null for a bare box.
 * @param enabled false greys the row out and ignores clicks.
 */
@Composable
public fun JwTriStateCheckbox(
    state: ToggleableState,
    onClick: () -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    CheckboxRow(
        state = state,
        label = label,
        enabled = enabled,
        modifier = modifier
            .jwFocusRing(interactionSource, JwShapes.small)
            .clip(JwShapes.small)
            .triStateToggleable(
                state = state,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    )
}

/** The box and label shared by both checkboxes; [modifier] carries the toggle behavior. */
@Composable
private fun CheckboxRow(
    state: ToggleableState,
    label: String?,
    enabled: Boolean,
    modifier: Modifier,
) {
    val colors = JwTheme.colors
    val marked = state != ToggleableState.Off
    val boxColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.onSurface.copy(alpha = DISABLED_BOX_ALPHA)
            marked -> colors.accent
            else -> Color.Transparent
        },
        label = "checkbox-box",
    )
    val markColor = if (enabled) colors.onAccent else colors.textDisabled
    Row(
        modifier = modifier
            .height(JwMetrics.controlHeight)
            .padding(horizontal = JwSpacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
    ) {
        Box(
            modifier = Modifier
                .size(JwCheckboxDefaults.size)
                .background(boxColor, JwShapes.extraSmall)
                .border(
                    JwMetrics.borderWidth,
                    if (marked && enabled) colors.accent else colors.controlBorder,
                    JwShapes.extraSmall,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                ToggleableState.On -> JwIcon(
                    imageVector = JwIcons.Check,
                    contentDescription = null,
                    tint = markColor,
                    modifier = Modifier.size(CheckMarkSize),
                )

                ToggleableState.Indeterminate -> Box(
                    modifier = Modifier
                        .width(IndeterminateMarkWidth)
                        .height(IndeterminateMarkHeight)
                        .background(markColor),
                )

                ToggleableState.Off -> Unit
            }
        }
        if (label != null) {
            JwText(
                text = label,
                style = JwTheme.textStyles.body,
                color = if (enabled) colors.onSurface else colors.textDisabled,
                maxLines = 1,
            )
        }
    }
}

/** Opacity of the box fill of a disabled checkbox. */
private const val DISABLED_BOX_ALPHA = 0.12f
