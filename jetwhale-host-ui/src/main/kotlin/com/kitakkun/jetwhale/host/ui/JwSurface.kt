package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

/**
 * A painted region: a background [color], an optional [border], and the content color that goes
 * with it, provided to everything inside through [LocalJwContentColor]. Use it as the root of a
 * screen so an off-screen capture has a background, or for a filled strip a plain `Box` would leave
 * transparent.
 *
 * Unlike a Material `Surface` it neither elevates nor swallows clicks; it is a background, nothing
 * more.
 *
 * @param color the fill; defaults to the theme's surface.
 * @param contentColor the color for text and icons inside; defaults to the theme's text color on
 * [JwColors.surface], so pass one along with any other [color].
 * @param shape clips the fill and the content.
 * @param border a hairline around the shape, or null for none.
 */
@Composable
public fun JwSurface(
    modifier: Modifier = Modifier,
    color: Color = JwTheme.colors.surface,
    contentColor: Color = JwTheme.colors.onSurface,
    shape: Shape = RectangleShape,
    border: BorderStroke? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clip(shape)
            .background(color),
    ) {
        CompositionLocalProvider(LocalJwContentColor provides contentColor, content = { content() })
    }
}
