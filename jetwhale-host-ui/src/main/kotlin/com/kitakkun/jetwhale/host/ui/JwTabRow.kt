package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of a [JwTabRow]. */
public object JwTabRowDefaults {
    /** Height of the strip. */
    public val height: Dp = 32.dp

    /** Thickness of the underline of the selected tab. */
    public val indicatorHeight: Dp = 2.dp
}

/**
 * A strip of [JwTab]s with a hairline underneath; the selected tab underlines itself in the
 * accent color. Views switch on the index they keep themselves. A strip wider than its pane
 * scrolls sideways rather than overflowing.
 *
 * @param tabs the [JwTab]s, in order.
 */
@Composable
public fun JwTabRow(
    modifier: Modifier = Modifier,
    tabs: @Composable RowScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(JwTabRowDefaults.height)
                .background(JwTheme.colors.toolbarBackground)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = JwSpacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.tiny),
            content = tabs,
        )
        JwHorizontalDivider()
    }
}

/**
 * One tab of a [JwTabRow].
 *
 * @param selected whether this tab's view is showing.
 * @param onClick what selecting the tab does.
 * @param text the tab's label.
 * @param count drawn after the text, the way "Traffic 12" reads.
 */
@Composable
public fun JwTab(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val textColor = when {
        selected -> MaterialTheme.colorScheme.onSurface
        hovered -> MaterialTheme.colorScheme.onSurface
        else -> JwTheme.colors.textSecondary
    }
    Column(
        modifier = modifier
            // Sized by the label, not by the row: the indicator below is fillMaxWidth and would
            // otherwise claim the whole strip for the first tab.
            .width(IntrinsicSize.Max)
            .fillMaxHeight()
            .jwFocusRing(interactionSource, MaterialTheme.shapes.small)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = JwSpacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                maxLines = 1,
            )
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = JwTheme.colors.textDisabled,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(JwTabRowDefaults.indicatorHeight)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}
