package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.kitakkun.jetwhale.host.ui.JwFocusRingStyle
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwMetrics
import com.kitakkun.jetwhale.host.ui.JwShapes
import com.kitakkun.jetwhale.host.ui.JwSnackbarDefaults
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.jwFocusRing

/**
 * The mirror's notice, floating at the bottom of the pane so it never pushes anything aside. It
 * looks like the host's snackbar and adds what that one lacks: several actions, a dismiss button,
 * a hold while it is hovered or focused, Esc to dismiss, and the reasons behind a partial failure.
 */
@Composable
internal fun MirrorNoticeHost(actions: MirrorNoticeActions, modifier: Modifier = Modifier) {
    val notice = actions.notice
    Box(modifier) {
        AnimatedVisibility(
            visible = notice != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            // Held through the exit animation, when the notice has already gone.
            val lastShown = remember { arrayOfNulls<MirrorNotice>(1) }
            if (notice != null) lastShown[0] = notice
            lastShown[0]?.let { NoticeStrip(it, actions) }
        }
    }
}

@Composable
private fun NoticeStrip(notice: MirrorNotice, actions: MirrorNoticeActions) {
    val interactionSource = remember(calculation = ::MutableInteractionSource)
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(notice, hovered || focused) { actions.hold(hovered || focused) }
    var showDetails by remember(notice) { mutableStateOf(false) }
    val colors = JwTheme.colors
    val content = if (notice.isError) colors.onErrorContainer else colors.onTooltip
    Column(
        modifier = Modifier
            .widthIn(max = JwSnackbarDefaults.maxWidth)
            .shadow(JwSpacing.small, JwShapes.medium)
            .background(if (notice.isError) colors.errorContainer else colors.tooltipBackground, JwShapes.medium)
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { event ->
                val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                if (escape) actions.dismiss()
                escape
            }
            .padding(start = JwSpacing.large, end = JwSpacing.small, top = JwSpacing.small, bottom = JwSpacing.small)
            .semantics { liveRegion = if (notice.isError) LiveRegionMode.Assertive else LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
            JwText(text = notice.message, style = JwTheme.textStyles.body, color = content, modifier = Modifier.weight(1f, fill = false))
            if (notice.details.isNotEmpty()) {
                NoticeButton(color = content, onClick = { showDetails = !showDetails }) {
                    JwText(text = if (showDetails) "Hide details" else "Details", style = JwTheme.textStyles.label, color = content, maxLines = 1)
                }
            }
            notice.actions.forEach { action ->
                NoticeButton(color = content, onClick = { actions.perform(action) }) {
                    JwText(text = action.label, style = JwTheme.textStyles.label, color = content, maxLines = 1)
                }
            }
            NoticeButton(color = content, onClick = actions::dismiss) {
                JwIcon(imageVector = CloseIcon, contentDescription = "Dismiss", tint = content)
            }
        }
        if (showDetails) {
            notice.details.forEach { reason ->
                JwText(text = reason, style = JwTheme.textStyles.bodySmall, color = content, modifier = Modifier.padding(top = JwSpacing.extraSmall))
            }
        }
    }
}

/** A button in the strip's own colors, since a JwButton's accent would fight the strip's background. */
@Composable
private fun NoticeButton(color: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interactionSource = remember(calculation = ::MutableInteractionSource)
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .height(JwMetrics.controlHeight)
            .jwFocusRing(interactionSource, JwShapes.small, JwFocusRingStyle.Outset)
            .clip(JwShapes.small)
            .background(if (hovered) color.copy(alpha = BUTTON_HOVER_ALPHA) else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = JwSpacing.medium),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private const val BUTTON_HOVER_ALPHA = 0.12f
