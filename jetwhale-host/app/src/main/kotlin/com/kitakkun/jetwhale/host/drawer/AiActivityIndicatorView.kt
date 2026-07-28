package com.kitakkun.jetwhale.host.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.ai_agent_connected
import com.kitakkun.jetwhale.host.ai_agent_operating
import org.jetbrains.compose.resources.stringResource

private const val PULSE_PERIOD_MILLIS = 1100
private const val AI_BORDER_ROTATION_PERIOD_MILLIS = 2000
private const val AI_BANNER_COLOR_FADE_MILLIS = 300

/**
 * Deliberately not a theme colour: this warm orange has to stand out against the (often primary-
 * tinted) selected drawer item, so it stays legible whether or not the operated plugin is selected.
 */
val AiOperatingAccentColor = Color(0xFFFF8A00)

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
    width: Dp = 2.dp,
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
        // minDimension/2 makes the corners fully round, matching the drawer item's pill.
        val radius = size.minDimension / 2f
        val ring = Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
            addRoundRect(
                RoundRect(
                    strokePx,
                    strokePx,
                    size.width - strokePx,
                    size.height - strokePx,
                    CornerRadius((radius - strokePx).coerceAtLeast(0f)),
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

/**
 * Banner shown in the expanded drawer header while an AI agent is attached over MCP.
 */
@Composable
fun AiActivityIndicatorView(
    uiState: AiActivityUiState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = uiState.isAgentConnected,
        // Collapse the banner's height as it goes, so the rest of the drawer slides up to fill the
        // space instead of the banner blinking out and leaving a gap.
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val pulseAlpha = aiActivityPulseAlpha(uiState.isOperating)
        // A fast tool call flips isOperating on and off within a few frames; animating the colour
        // turns that into a soft glow instead of a jarring flicker between the two container colours.
        val containerColor by animateColorAsState(
            targetValue = if (uiState.isOperating) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            animationSpec = tween(AI_BANNER_COLOR_FADE_MILLIS),
            label = "ai-banner-color",
        )
        Surface(
            color = containerColor,
            shape = MaterialTheme.shapes.medium,
            modifier = modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .alpha(pulseAlpha),
                )
                // Animate the height so the tool-name line slides in and out instead of snapping.
                Column(modifier = Modifier.animateContentSize()) {
                    Text(
                        text = stringResource(Res.string.ai_agent_connected),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    uiState.operatingToolName?.let { toolName ->
                        Text(
                            text = toolName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Icon-only variant for the shrunk drawer, where the tool name has to live in a tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactAiActivityIndicatorView(
    uiState: AiActivityUiState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = uiState.isAgentConnected) {
        val pulseAlpha = aiActivityPulseAlpha(uiState.isOperating)
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Right),
            tooltip = {
                PlainTooltip {
                    Text(uiState.operatingToolName ?: stringResource(Res.string.ai_agent_connected))
                }
            },
            state = rememberTooltipState(),
        ) {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = stringResource(
                        if (uiState.isOperating) Res.string.ai_agent_operating else Res.string.ai_agent_connected,
                    ),
                    tint = if (uiState.isOperating) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .size(24.dp)
                        .alpha(pulseAlpha),
                )
            }
        }
    }
}

@Preview
@Composable
private fun AiActivityIndicatorConnectedPreview() {
    AiActivityIndicatorView(
        uiState = AiActivityUiState(isAgentConnected = true, operatingToolName = null),
    )
}

@Preview
@Composable
private fun AiActivityIndicatorOperatingPreview() {
    AiActivityIndicatorView(
        uiState = AiActivityUiState(isAgentConnected = true, operatingToolName = "jetwhale.click"),
    )
}
