package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
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

/**
 * A row of joined buttons of which exactly one is [selected]: a small, always-visible choice such
 * as a view mode or a match rule, where a dropdown would hide the options. Named after Material's
 * `SingleChoiceSegmentedButtonRow`, at one control's height.
 *
 * @param T the option type.
 * @param options the choices, in display order.
 * @param selected the current choice.
 * @param onSelect called with the option clicked.
 * @param label the text for an option.
 * @param enabled false greys the group out and ignores clicks.
 */
@Composable
public fun <T> JwSegmentedButtons(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = MaterialTheme.shapes.small
    val scheme = MaterialTheme.colorScheme
    val colors = JwTheme.colors
    Row(
        modifier = modifier
            .height(JwMetrics.controlHeight)
            .clip(shape)
            .border(JwMetrics.borderWidth, if (enabled) scheme.outline else colors.border.copy(alpha = 0.5f), shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) JwVerticalDivider()
            val isSelected = option == selected
            val interactionSource = remember { MutableInteractionSource() }
            val hovered by interactionSource.collectIsHoveredAsState()
            val background = when {
                isSelected -> colors.selection
                hovered && enabled -> colors.hover
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .height(JwMetrics.controlHeight)
                    .background(background)
                    .jwFocusRing(interactionSource, shape)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onSelect(option) },
                    )
                    .padding(horizontal = JwSpacing.large),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        !enabled -> colors.textDisabled
                        isSelected -> scheme.onSurface
                        else -> colors.textSecondary
                    },
                    maxLines = 1,
                )
            }
        }
    }
}
