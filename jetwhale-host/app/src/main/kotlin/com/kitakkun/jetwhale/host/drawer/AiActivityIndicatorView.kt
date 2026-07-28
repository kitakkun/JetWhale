package com.kitakkun.jetwhale.host.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.ai_agent_connected
import com.kitakkun.jetwhale.host.ai_agent_operating
import org.jetbrains.compose.resources.stringResource

private const val PULSE_PERIOD_MILLIS = 600

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
            animation = tween(PULSE_PERIOD_MILLIS),
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
    AnimatedVisibility(visible = uiState.isAgentConnected) {
        val pulseAlpha = aiActivityPulseAlpha(uiState.isOperating)
        Surface(
            color = if (uiState.isOperating) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
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
                Column {
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
                    contentDescription = stringResource(Res.string.ai_agent_operating),
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
