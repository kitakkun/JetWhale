package com.kitakkun.jetwhale.host.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.ai_agent_connected
import com.kitakkun.jetwhale.host.ai_agent_idle
import com.kitakkun.jetwhale.host.ai_agent_operating_tooltip
import com.kitakkun.jetwhale.host.ai_operating_app
import com.kitakkun.jetwhale.host.ai_operating_plugin
import com.kitakkun.jetwhale.host.ai_operating_tool
import com.kitakkun.jetwhale.host.follow_ai_description
import com.kitakkun.jetwhale.host.follow_ai_title
import com.kitakkun.jetwhale.host.ui.JwDropdownMenu
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwMetrics
import com.kitakkun.jetwhale.host.ui.JwShapes
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwStatusDot
import com.kitakkun.jetwhale.host.ui.JwSurface
import com.kitakkun.jetwhale.host.ui.JwSwitch
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwTooltip
import org.jetbrains.compose.resources.stringResource

private const val PULSE_PERIOD_MILLIS = 1100
private const val AI_BORDER_ROTATION_PERIOD_MILLIS = 2000

/**
 * A border whose highlight sweeps continuously around the shape, marking the element an AI agent is
 * currently operating. The gradient runs colour → transparent → colour so its ends meet, and the
 * whole ring rotates.
 *
 * The ring is drawn by clipping to the gap between the outer shape and an inset copy, then filling
 * that gap with a rotating sweep gradient — so the shape stays put while only the highlight travels.
 */
@Composable
fun Modifier.aiOperatingBorder(
    color: Color,
    width: Dp,
    cornerRadius: Dp,
): Modifier {
    val transition = rememberInfiniteTransition(label = "ai-operating-border")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(AI_BORDER_ROTATION_PERIOD_MILLIS, easing = LinearEasing),
        ),
        label = "ai-operating-border-angle",
    )
    // Two opposite highlights with transparent gaps between them, so a pair of beams sweeps around.
    val brushColors = listOf(color, Color.Transparent, color, Color.Transparent, color)
    return drawWithCache {
        val strokePx = width.toPx()
        val radius = cornerRadius.toPx()
        val ring = Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    cornerRadius = CornerRadius(radius),
                ),
            )
            addRoundRect(
                RoundRect(
                    left = strokePx,
                    top = strokePx,
                    right = size.width - strokePx,
                    bottom = size.height - strokePx,
                    cornerRadius = CornerRadius((radius - strokePx).coerceAtLeast(0f)),
                ),
            )
        }
        // Large enough to cover the ring at any rotation.
        val cover = size.maxDimension * 2f
        onDrawWithContent {
            drawContent()
            clipPath(ring) {
                rotate(angle) {
                    drawRect(
                        brush = Brush.sweepGradient(brushColors, center = center),
                        topLeft = Offset(center.x - cover / 2f, center.y - cover / 2f),
                        size = Size(cover, cover),
                    )
                }
            }
        }
    }
}

/**
 * Pulses only while an operation is in flight. An always-running animation would keep the host
 * requesting frames for a debugger that is usually sitting idle.
 */
@Composable
fun aiActivityPulseAlpha(operating: Boolean): Float {
    if (!operating) return 1f
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            // Sine easing on each leg gives a soft breathing pulse rather than a hard blink.
            animation = tween(PULSE_PERIOD_MILLIS, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    return alpha
}

/** The popover is a short status card, not a menu, so it is kept to a readable measure. */
private val AiDetailsMaxWidth = 280.dp

/** The ring's corner on the header banner, matching the small shape of the surface it rings. */
private val AiBannerRingCornerRadius = 6.dp

/**
 * The sidebar header's AI banner, the window's one sign of an AI agent attached over MCP. It fills
 * the header row while an agent is connected — the AI mark and *AI agent connected*, or the tool with
 * the sweeping ring while a call runs — and leaves the row empty otherwise. The row's height never
 * changes, so nothing below it moves as agents come and go. Clicking it opens the details and the
 * follow switch. Which plugin is being operated stays marked by that plugin's own ring.
 */
@Composable
fun AiActivityBanner(
    uiState: AiActivityUiState,
    onFollowChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = uiState.isAgentConnected,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        var detailsShown by remember { mutableStateOf(false) }
        Box {
            JwTooltip(text = uiState.operatingToolName?.let { stringResource(Res.string.ai_agent_operating_tooltip, it) }) {
                AiActivityBannerSurface(
                    operatingToolName = uiState.operatingToolName,
                    operatingToolShortName = uiState.operatingToolShortName,
                    onClick = { detailsShown = true },
                )
            }
            JwDropdownMenu(expanded = detailsShown, onDismissRequest = { detailsShown = false }) {
                AiActivityDetails(uiState = uiState, onFollowChange = onFollowChange)
            }
        }
    }
}

@Composable
private fun AiActivityBannerSurface(
    operatingToolName: String?,
    operatingToolShortName: String?,
    onClick: () -> Unit,
) {
    val operating = operatingToolName != null
    JwSurface(
        color = if (operating) JwTone.Warning.containerColor else JwTheme.colors.elevatedBackground,
        shape = JwShapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clip(JwShapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .then(
                if (operating) {
                    Modifier.aiOperatingBorder(color = JwTheme.colors.aiAccent, width = JwMetrics.focusStrokeWidth, cornerRadius = AiBannerRingCornerRadius)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = JwSpacing.medium, vertical = JwSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JwIcon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = operatingToolName?.let { stringResource(Res.string.ai_operating_tool, it) },
                tint = if (operating) JwTheme.colors.aiAccent else JwTheme.colors.textSecondary,
                modifier = Modifier.alpha(aiActivityPulseAlpha(operating)),
            )
            JwText(
                // The ring already says a call is running; the line only has to say which.
                text = operatingToolShortName ?: stringResource(Res.string.ai_agent_connected),
                style = JwTheme.textStyles.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The collapsed rail's form of [AiActivityBanner]: the AI mark alone, with the ring while a call
 * runs, the state and tool in its tooltip, and the same details on click. Its slot is kept while no
 * agent is connected, so the rail below it never moves.
 */
@Composable
fun AiActivityIndicator(
    uiState: AiActivityUiState,
    onFollowChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(JwMetrics.controlHeight), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = uiState.isAgentConnected,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
        ) {
            var detailsShown by remember { mutableStateOf(false) }
            val description = uiState.operatingToolName
                ?.let { stringResource(Res.string.ai_agent_operating_tooltip, it) }
                ?: stringResource(Res.string.ai_agent_connected)
            Box {
                JwIconButton(
                    onClick = { detailsShown = true },
                    tooltip = description,
                    modifier = if (uiState.isOperating) {
                        Modifier.aiOperatingBorder(color = JwTheme.colors.aiAccent, width = JwMetrics.focusStrokeWidth, cornerRadius = AiBannerRingCornerRadius)
                    } else {
                        Modifier
                    },
                ) {
                    JwIcon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = description,
                        tint = if (uiState.isOperating) JwTheme.colors.aiAccent else JwTheme.colors.textSecondary,
                        modifier = Modifier.alpha(aiActivityPulseAlpha(uiState.isOperating)),
                    )
                }
                JwStatusDot(tone = JwTone.Success, modifier = Modifier.align(Alignment.BottomEnd))
                JwDropdownMenu(expanded = detailsShown, onDismissRequest = { detailsShown = false }) {
                    AiActivityDetails(uiState = uiState, onFollowChange = onFollowChange)
                }
            }
        }
    }
}

@Composable
private fun AiActivityDetails(
    uiState: AiActivityUiState,
    onFollowChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.widthIn(max = AiDetailsMaxWidth)) {
        Column(
            modifier = Modifier.padding(horizontal = JwSpacing.medium, vertical = JwSpacing.small),
            verticalArrangement = Arrangement.spacedBy(JwSpacing.tiny),
        ) {
            JwText(text = stringResource(Res.string.ai_agent_connected), style = JwTheme.textStyles.subtitle)
            when (val toolName = uiState.operatingToolName) {
                null -> JwText(text = stringResource(Res.string.ai_agent_idle), style = JwTheme.textStyles.bodySmall, color = JwTheme.colors.textSecondary)

                else -> {
                    JwText(text = stringResource(Res.string.ai_operating_tool, toolName), style = JwTheme.textStyles.code)
                    uiState.operatingPluginName?.let {
                        JwText(text = stringResource(Res.string.ai_operating_plugin, it), style = JwTheme.textStyles.bodySmall, color = JwTheme.colors.textSecondary)
                    }
                    uiState.operatingAppName?.let {
                        JwText(text = stringResource(Res.string.ai_operating_app, it), style = JwTheme.textStyles.bodySmall, color = JwTheme.colors.textSecondary)
                    }
                }
            }
        }
        JwHorizontalDivider()
        Row(
            modifier = Modifier.padding(horizontal = JwSpacing.medium, vertical = JwSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val title = stringResource(Res.string.follow_ai_title)
            Column(modifier = Modifier.weight(1f)) {
                JwText(text = title, style = JwTheme.textStyles.label)
                JwText(text = stringResource(Res.string.follow_ai_description), style = JwTheme.textStyles.bodySmall, color = JwTheme.colors.textSecondary)
            }
            JwSwitch(checked = uiState.isFollowModeOn, contentDescription = title, onCheckedChange = onFollowChange)
        }
    }
}

@Preview
@Composable
private fun AiActivityBannerConnectedPreview() {
    AiActivityBanner(
        uiState = AiActivityUiState(isAgentConnected = true, operatingToolName = null, operatingToolShortName = null, operatingPluginName = null, operatingAppName = null, isFollowModeOn = true),
        onFollowChange = {},
    )
}

@Preview
@Composable
private fun AiActivityBannerOperatingPreview() {
    AiActivityBanner(
        uiState = AiActivityUiState(isAgentConnected = true, operatingToolName = "com.kitakkun.jetwhale.mirror.tap", operatingToolShortName = "mirror.tap", operatingPluginName = "Device Mirror", operatingAppName = null, isFollowModeOn = true),
        onFollowChange = {},
    )
}

@Preview
@Composable
private fun AiActivityIndicatorOperatingPreview() {
    AiActivityIndicator(
        uiState = AiActivityUiState(
            isAgentConnected = true,
            operatingToolName = "com.kitakkun.jetwhale.mirror.tap",
            operatingToolShortName = "mirror.tap",
            operatingPluginName = "Device Mirror",
            operatingAppName = null,
            isFollowModeOn = true,
        ),
        onFollowChange = {},
    )
}

@Preview
@Composable
private fun AiActivityDetailsPreview() {
    AiActivityDetails(
        uiState = AiActivityUiState(
            isAgentConnected = true,
            operatingToolName = "com.kitakkun.jetwhale.mirror.tap",
            operatingToolShortName = "mirror.tap",
            operatingPluginName = "Device Mirror",
            operatingAppName = null,
            isFollowModeOn = true,
        ),
        onFollowChange = {},
    )
}
