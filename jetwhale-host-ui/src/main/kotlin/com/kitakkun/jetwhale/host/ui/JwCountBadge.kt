package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** Sizes of a [JwCountBadge]. */
public object JwCountBadgeDefaults {
    /** Height of the badge, the same as a [JwTag]. */
    public val height: Dp = JwTagDefaults.height

    /** The badge is at least as wide as it is tall, so a single digit still reads as a pill. */
    public val minWidth: Dp = height
}

/**
 * A small pill with a number: how many calls a tool has made, how many rows a filter matches.
 * Sits beside a label on a row or a tab without stretching it.
 *
 * @param count the number shown, as is — format thousands yourself if the value can be large.
 * @param tone [JwTone.Neutral] for an ordinary count; a stronger tone for one that demands
 * attention, such as an error count or an operation in flight.
 */
@Composable
public fun JwCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    tone: JwTone = JwTone.Neutral,
) {
    val shape = MaterialTheme.shapes.extraSmall
    val filled = tone != JwTone.Neutral
    Box(
        modifier = modifier
            .height(JwCountBadgeDefaults.height)
            .defaultMinSize(minWidth = JwCountBadgeDefaults.minWidth)
            .background(if (filled) tone.color else tone.containerColor, shape)
            .padding(horizontal = JwSpacing.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = JwTypography.code,
            color = if (filled) tone.onColor else tone.onContainerColor,
            maxLines = 1,
        )
    }
}
