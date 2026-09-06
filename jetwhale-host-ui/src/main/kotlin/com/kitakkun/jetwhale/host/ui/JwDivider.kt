package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A hairline between stacked panes or rows, filling the available width. */
@Composable
public fun JwHorizontalDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(JwMetrics.borderWidth)
            .background(JwTheme.colors.border),
    )
}

/** A hairline between panes placed side by side, filling the available height. */
@Composable
public fun JwVerticalDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(JwMetrics.borderWidth)
            .background(JwTheme.colors.border),
    )
}
