package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay

/** Timing of a [JwTooltip]. */
public object JwTooltipDefaults {
    /** How long the pointer rests on a control before its tooltip appears. */
    public const val SHOW_DELAY_MILLIS: Long = 500
}

/**
 * Shows [text] in a small tooltip while the pointer rests on [content]. A null [text] renders the
 * content bare, so callers can pass an optional label straight through.
 *
 * The tooltip is a popup below the control (above, when the window has no room), on the theme's
 * inverse surface so it floats over any pane. It appears after [JwTooltipDefaults.SHOW_DELAY_MILLIS]
 * and leaves with the pointer.
 *
 * @param text the tooltip's text, or null for no tooltip.
 * @param content the composable the tooltip describes.
 */
@Composable
public fun JwTooltip(
    text: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (text == null) {
        Box(modifier = modifier) { content() }
        return
    }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(hovered) {
        if (hovered) {
            delay(JwTooltipDefaults.SHOW_DELAY_MILLIS)
            visible = true
        } else {
            visible = false
        }
    }
    Box(modifier = modifier.hoverable(interactionSource)) {
        content()
        if (visible) {
            Popup(popupPositionProvider = rememberJwPopupPositionProvider(JwPopupAnchor.BelowCenter)) {
                Box(
                    modifier = Modifier
                        .shadow(TooltipShadowElevation, JwShapes.small)
                        .background(JwTheme.colors.tooltipBackground, JwShapes.small)
                        .padding(horizontal = JwSpacing.medium, vertical = JwSpacing.extraSmall),
                ) {
                    JwText(
                        text = text,
                        style = JwTheme.textStyles.labelSmall,
                        color = JwTheme.colors.onTooltip,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Shadow under a tooltip. */
private val TooltipShadowElevation = JwSpacing.extraSmall
