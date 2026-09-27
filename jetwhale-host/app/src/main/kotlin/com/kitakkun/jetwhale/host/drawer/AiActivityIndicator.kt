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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.ai_agent_connected
import com.kitakkun.jetwhale.host.ai_agent_idle
import com.kitakkun.jetwhale.host.ai_agent_operating_tooltip
import com.kitakkun.jetwhale.host.ai_connect_agent
import com.kitakkun.jetwhale.host.ai_connect_agent_description
import com.kitakkun.jetwhale.host.ai_mcp_claude_code
import com.kitakkun.jetwhale.host.ai_mcp_copy
import com.kitakkun.jetwhale.host.ai_mcp_endpoint
import com.kitakkun.jetwhale.host.ai_mcp_failed_description
import com.kitakkun.jetwhale.host.ai_mcp_off
import com.kitakkun.jetwhale.host.ai_mcp_off_description
import com.kitakkun.jetwhale.host.ai_mcp_open_guide
import com.kitakkun.jetwhale.host.ai_mcp_open_settings
import com.kitakkun.jetwhale.host.ai_mcp_other_clients
import com.kitakkun.jetwhale.host.ai_mcp_starting
import com.kitakkun.jetwhale.host.ai_operating_app
import com.kitakkun.jetwhale.host.ai_operating_plugin
import com.kitakkun.jetwhale.host.ai_operating_tool
import com.kitakkun.jetwhale.host.follow_ai_description
import com.kitakkun.jetwhale.host.follow_ai_title
import com.kitakkun.jetwhale.host.model.McpClientSetup
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
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
import com.kitakkun.jetwhale.host.ui.jwFocusRing
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

/** Wide enough for the endpoint and the Claude Code command to read on few lines. */
private val AiConnectHelpWidth = 360.dp

/** The ring's corner on the header card, matching the small shape of the card it rings. */
private val AiBannerRingCornerRadius = 6.dp

/**
 * The sidebar header's AI card, the window's one place for an AI agent over MCP. It is always there,
 * so the header row never changes, and says what the agent side is at now: MCP off, waiting for a
 * client, an agent connected, or the tool a call runs with the sweeping ring. Clicking it opens what
 * fits: how to start MCP, how to connect a client, or the details and the follow switch. Which plugin
 * is being operated stays marked by that plugin's own ring.
 */
@Composable
fun AiActivityBanner(
    uiState: AiActivityUiState,
    onFollowChange: (Boolean) -> Unit,
    onOpenMcpSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var detailsShown by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        JwTooltip(text = uiState.operatingToolName?.let { stringResource(Res.string.ai_agent_operating_tooltip, it) }) {
            AiActivityCard(uiState = uiState, onClick = { detailsShown = true })
        }
        JwDropdownMenu(expanded = detailsShown, onDismissRequest = { detailsShown = false }) {
            AiActivityPopover(
                uiState = uiState,
                onFollowChange = onFollowChange,
                onOpenMcpSettings = {
                    detailsShown = false
                    onOpenMcpSettings()
                },
            )
        }
    }
}

@Composable
private fun AiActivityCard(
    uiState: AiActivityUiState,
    onClick: () -> Unit,
) {
    val operating = uiState.isOperating
    val interactionSource = remember(calculation = ::MutableInteractionSource)
    val hovered by interactionSource.collectIsHoveredAsState()
    JwSurface(
        color = aiCardColor(connected = uiState.isAgentConnected, operating = uiState.isOperating),
        shape = JwShapes.small,
        border = BorderStroke(JwMetrics.borderWidth, JwTheme.colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .jwFocusRing(interactionSource, JwShapes.small)
            .clip(JwShapes.small)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .then(
                if (operating) {
                    Modifier.aiOperatingBorder(
                        color = JwTheme.colors.aiAccent,
                        width = JwMetrics.focusStrokeWidth,
                        cornerRadius = AiBannerRingCornerRadius,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .background(if (hovered) JwTheme.colors.hover else Color.Transparent)
                .padding(horizontal = JwSpacing.medium, vertical = JwSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JwIcon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = uiState.operatingToolName?.let { stringResource(Res.string.ai_operating_tool, it) },
                tint = aiIconTint(connected = uiState.isAgentConnected, operating = uiState.isOperating),
                modifier = Modifier.alpha(aiActivityPulseAlpha(operating)),
            )
            JwText(
                // The ring already says a call is running; the line only has to say which.
                text = uiState.operatingToolShortName ?: aiCardLabel(connected = uiState.isAgentConnected, mcpServer = uiState.mcpServer),
                style = JwTheme.textStyles.label,
                color = if (uiState.isAgentConnected) JwTheme.colors.onSurface else JwTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (uiState.isAgentConnected && !operating) JwStatusDot(tone = JwTone.Success)
            JwIcon(imageVector = Icons.Default.ExpandMore, contentDescription = null, tint = JwTheme.colors.textSecondary)
        }
    }
}

/**
 * The collapsed rail's form of [AiActivityBanner]: the AI mark alone in a small card, muted until an
 * agent connects, with the ring while a call runs, the state in its tooltip, and the same popover on
 * click.
 */
@Composable
fun AiActivityIndicator(
    uiState: AiActivityUiState,
    onFollowChange: (Boolean) -> Unit,
    onOpenMcpSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var detailsShown by remember { mutableStateOf(false) }
    val interactionSource = remember(calculation = ::MutableInteractionSource)
    val hovered by interactionSource.collectIsHoveredAsState()
    val description = uiState.operatingToolName?.let { stringResource(Res.string.ai_agent_operating_tooltip, it) } ?: aiCardLabel(
        connected = uiState.isAgentConnected,
        mcpServer = uiState.mcpServer,
    )
    Box(modifier = modifier) {
        JwTooltip(text = description) {
            JwSurface(
                color = if (hovered) {
                    JwTheme.colors.hover
                } else {
                    aiCardColor(
                        connected = uiState.isAgentConnected,
                        operating = uiState.isOperating,
                    )
                },
                shape = JwShapes.small,
                border = BorderStroke(JwMetrics.borderWidth, JwTheme.colors.border),
                modifier = Modifier
                    .size(JwMetrics.controlHeight)
                    .jwFocusRing(interactionSource, JwShapes.small)
                    .clip(JwShapes.small)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = description,
                        onClick = { detailsShown = true },
                    )
                    .then(
                        if (uiState.isOperating) {
                            Modifier.aiOperatingBorder(
                                color = JwTheme.colors.aiAccent,
                                width = JwMetrics.focusStrokeWidth,
                                cornerRadius = AiBannerRingCornerRadius,
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                JwIcon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = description,
                    tint = aiIconTint(connected = uiState.isAgentConnected, operating = uiState.isOperating),
                    modifier = Modifier.align(Alignment.Center).alpha(aiActivityPulseAlpha(uiState.isOperating)),
                )
            }
        }
        if (uiState.isAgentConnected) JwStatusDot(tone = JwTone.Success, modifier = Modifier.align(Alignment.BottomEnd))
        JwDropdownMenu(expanded = detailsShown, onDismissRequest = { detailsShown = false }) {
            AiActivityPopover(
                uiState = uiState,
                onFollowChange = onFollowChange,
                onOpenMcpSettings = {
                    detailsShown = false
                    onOpenMcpSettings()
                },
            )
        }
    }
}

/** A connected agent's card stands out; until one connects the card stays in the background. */
@Composable
private fun aiCardColor(connected: Boolean, operating: Boolean): Color = when {
    operating -> JwTone.Warning.containerColor
    connected -> JwTheme.colors.elevatedBackground
    else -> Color.Transparent
}

@Composable
private fun aiIconTint(connected: Boolean, operating: Boolean): Color = when {
    operating -> JwTheme.colors.aiAccent
    connected -> JwTheme.colors.onSurface
    else -> JwTheme.colors.textSecondary
}

@Composable
private fun aiCardLabel(connected: Boolean, mcpServer: McpServerAvailability): String = when {
    connected -> stringResource(Res.string.ai_agent_connected)

    else -> when (mcpServer) {
        is McpServerAvailability.Ready -> stringResource(Res.string.ai_connect_agent)
        is McpServerAvailability.Starting -> stringResource(Res.string.ai_mcp_starting)
        is McpServerAvailability.Off -> stringResource(Res.string.ai_mcp_off)
    }
}

@Composable
private fun AiActivityPopover(
    uiState: AiActivityUiState,
    onFollowChange: (Boolean) -> Unit,
    onOpenMcpSettings: () -> Unit,
) {
    if (uiState.isAgentConnected) {
        AiActivityDetails(
            operatingToolName = uiState.operatingToolName,
            operatingPluginName = uiState.operatingPluginName,
            operatingAppName = uiState.operatingAppName,
            isFollowModeOn = uiState.isFollowModeOn,
            onFollowChange = onFollowChange,
        )
        return
    }
    when (val server = uiState.mcpServer) {
        is McpServerAvailability.Ready -> McpConnectHelp(setup = server.setup, onOpenMcpSettings = onOpenMcpSettings)

        is McpServerAvailability.Starting -> McpOffHelp(
            title = stringResource(Res.string.ai_mcp_starting),
            description = null,
            onOpenMcpSettings = onOpenMcpSettings,
        )

        is McpServerAvailability.Off -> McpOffHelp(
            title = stringResource(Res.string.ai_mcp_off),
            description = server.reason?.let { stringResource(Res.string.ai_mcp_failed_description, it) }
                ?: stringResource(Res.string.ai_mcp_off_description),
            onOpenMcpSettings = onOpenMcpSettings,
        )
    }
}

@Composable
private fun AiActivityDetails(
    operatingToolName: String?,
    operatingPluginName: String?,
    operatingAppName: String?,
    isFollowModeOn: Boolean,
    onFollowChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.widthIn(max = AiDetailsMaxWidth)) {
        Column(
            modifier = Modifier.padding(horizontal = JwSpacing.medium, vertical = JwSpacing.small),
            verticalArrangement = Arrangement.spacedBy(JwSpacing.tiny),
        ) {
            JwText(text = stringResource(Res.string.ai_agent_connected), style = JwTheme.textStyles.subtitle)
            when (val toolName = operatingToolName) {
                null -> JwText(
                    text = stringResource(Res.string.ai_agent_idle),
                    style = JwTheme.textStyles.bodySmall,
                    color = JwTheme.colors.textSecondary,
                )

                else -> {
                    JwText(text = stringResource(Res.string.ai_operating_tool, toolName), style = JwTheme.textStyles.code)
                    operatingPluginName?.let {
                        JwText(
                            text = stringResource(Res.string.ai_operating_plugin, it),
                            style = JwTheme.textStyles.bodySmall,
                            color = JwTheme.colors.textSecondary,
                        )
                    }
                    operatingAppName?.let {
                        JwText(
                            text = stringResource(Res.string.ai_operating_app, it),
                            style = JwTheme.textStyles.bodySmall,
                            color = JwTheme.colors.textSecondary,
                        )
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
                JwText(
                    text = stringResource(Res.string.follow_ai_description),
                    style = JwTheme.textStyles.bodySmall,
                    color = JwTheme.colors.textSecondary,
                )
            }
            JwSwitch(checked = isFollowModeOn, contentDescription = title, onCheckedChange = onFollowChange)
        }
    }
}

/** How to point a client at the running server: the endpoint and ready-to-paste setups. */
@Composable
private fun McpConnectHelp(
    setup: McpClientSetup,
    onOpenMcpSettings: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val copy = stringResource(Res.string.ai_mcp_copy)
    Column(
        modifier = Modifier.width(AiConnectHelpWidth).padding(horizontal = JwSpacing.medium, vertical = JwSpacing.small),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.small),
    ) {
        JwText(text = stringResource(Res.string.ai_connect_agent), style = JwTheme.textStyles.subtitle)
        JwText(
            text = stringResource(Res.string.ai_connect_agent_description),
            style = JwTheme.textStyles.bodySmall,
            color = JwTheme.colors.textSecondary,
        )
        // The menu scrolls past its max height; the ways out stay above the snippets that push it there.
        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
            JwButton(
                text = stringResource(Res.string.ai_mcp_open_guide),
                onClick = { uriHandler.openUri(McpClientSetup.GUIDE_URL) },
                style = JwButtonStyle.Text,
            )
            JwButton(text = stringResource(Res.string.ai_mcp_open_settings), onClick = onOpenMcpSettings, style = JwButtonStyle.Text)
        }
        JwText(text = stringResource(Res.string.ai_mcp_endpoint), style = JwTheme.textStyles.label)
        JwCodeBlock(text = setup.endpointUrl, copyLabel = copy)
        JwText(text = stringResource(Res.string.ai_mcp_claude_code), style = JwTheme.textStyles.label)
        JwCodeBlock(text = setup.claudeCodeCommand, wrap = true, copyLabel = copy)
        JwText(text = stringResource(Res.string.ai_mcp_other_clients), style = JwTheme.textStyles.label)
        JwCodeBlock(text = setup.jsonConfig, copyLabel = copy)
    }
}

/** Why no agent can connect now, and the way to the settings that change it. */
@Composable
private fun McpOffHelp(
    title: String,
    description: String?,
    onOpenMcpSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = AiDetailsMaxWidth).padding(horizontal = JwSpacing.medium, vertical = JwSpacing.small),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.small),
    ) {
        JwText(text = title, style = JwTheme.textStyles.subtitle)
        description?.let { JwText(text = it, style = JwTheme.textStyles.bodySmall, color = JwTheme.colors.textSecondary) }
        JwButton(text = stringResource(Res.string.ai_mcp_open_settings), onClick = onOpenMcpSettings)
    }
}

private val PreviewSetup = McpClientSetup.forServer(host = "localhost", port = 7080)

private val PreviewConnected = AiActivityUiState.Idle.copy(
    isAgentConnected = true,
    isFollowModeOn = true,
    mcpServer = McpServerAvailability.Ready(PreviewSetup),
)

private val PreviewOperating = PreviewConnected.copy(
    operatingToolName = "com.kitakkun.jetwhale.mirror.tap",
    operatingToolShortName = "mirror.tap",
    operatingPluginName = "Device Mirror",
)

/** MCP off, waiting for a client, connected, operating: the card's four states from quiet to busy. */
private val PreviewStates = listOf(
    AiActivityUiState.Idle,
    AiActivityUiState.Idle.copy(mcpServer = McpServerAvailability.Ready(PreviewSetup)),
    PreviewConnected,
    PreviewOperating,
)

@Preview
@Composable
private fun AiActivityBannerLightPreview() {
    AiActivityPreviewColumn(darkTheme = false) {
        PreviewStates.forEach { AiActivityBanner(uiState = it, onFollowChange = {}, onOpenMcpSettings = {}) }
    }
}

@Preview
@Composable
private fun AiActivityBannerDarkPreview() {
    AiActivityPreviewColumn(darkTheme = true) {
        PreviewStates.forEach { AiActivityBanner(uiState = it, onFollowChange = {}, onOpenMcpSettings = {}) }
    }
}

@Preview
@Composable
private fun AiActivityIndicatorLightPreview() {
    AiActivityPreviewColumn(darkTheme = false) {
        PreviewStates.forEach { AiActivityIndicator(uiState = it, onFollowChange = {}, onOpenMcpSettings = {}) }
    }
}

@Preview
@Composable
private fun AiActivityIndicatorDarkPreview() {
    AiActivityPreviewColumn(darkTheme = true) {
        PreviewStates.forEach { AiActivityIndicator(uiState = it, onFollowChange = {}, onOpenMcpSettings = {}) }
    }
}

@Preview
@Composable
private fun AiActivityDetailsPreview() {
    JwTheme(darkTheme = false) {
        AiActivityDetails(
            operatingToolName = "com.kitakkun.jetwhale.mirror.tap",
            operatingPluginName = "Device Mirror",
            operatingAppName = null,
            isFollowModeOn = true,
            onFollowChange = {},
        )
    }
}

@Preview
@Composable
private fun McpConnectHelpPreview() {
    JwTheme(darkTheme = true) {
        McpConnectHelp(setup = PreviewSetup, onOpenMcpSettings = {})
    }
}

@Preview
@Composable
private fun McpOffHelpPreview() {
    JwTheme(darkTheme = false) {
        McpOffHelp(title = "MCP is off", description = "The MCP server couldn't start: port 7080 is in use", onOpenMcpSettings = {})
    }
}

@Composable
private fun AiActivityPreviewColumn(darkTheme: Boolean, content: @Composable () -> Unit) {
    JwTheme(darkTheme = darkTheme) {
        Column(
            modifier = Modifier.width(260.dp).background(JwTheme.colors.sidebarBackground).padding(JwSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(JwSpacing.small),
        ) {
            content()
        }
    }
}
