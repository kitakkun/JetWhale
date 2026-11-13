package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.expand_sidebar
import com.kitakkun.jetwhale.host.info
import com.kitakkun.jetwhale.host.mcp_tools_open_all
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.PluginIconResource
import com.kitakkun.jetwhale.host.no_session_available
import com.kitakkun.jetwhale.host.puzzle_filled
import com.kitakkun.jetwhale.host.puzzle_outlined
import com.kitakkun.jetwhale.host.rail_plugin_in_app
import com.kitakkun.jetwhale.host.rail_plugin_in_host_session
import com.kitakkun.jetwhale.host.settings
import com.kitakkun.jetwhale.host.sidebar_unfold
import com.kitakkun.jetwhale.host.ui.JwDropdownMenu
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwMetrics
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwStatusDot
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.decodeToSvgPainter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The sidebar collapsed to an icon rail: every entry keeps its place, and its label moves to a tooltip. */
@Composable
fun ShrunkToolingDrawerView(
    plugins: ImmutableList<DrawerPluginItemUiState>,
    sessions: ImmutableList<DebugSession>,
    selectedSession: DebugSession?,
    aiActivity: AiActivityUiState,
    onFollowAiOperationChange: (Boolean) -> Unit,
    onOpenMcpSettings: () -> Unit,
    onClickExpandMenu: () -> Unit,
    onClickSettings: () -> Unit,
    onClickPlugin: (DrawerPluginItemUiState) -> Unit,
    onClickInfo: () -> Unit,
    onOpenAllMcpTools: () -> Unit,
    onSelectSession: (DebugSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(JwMetrics.railWidth)
            .background(JwTheme.colors.sidebarBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.height(JwMetrics.toolbarHeight),
            contentAlignment = Alignment.Center,
        ) {
            JwIconButton(onClick = onClickExpandMenu, tooltip = stringResource(Res.string.expand_sidebar)) {
                JwIcon(painter = painterResource(Res.drawable.sidebar_unfold), contentDescription = null)
            }
        }
        AiActivityIndicator(
            uiState = aiActivity,
            onFollowChange = onFollowAiOperationChange,
            onOpenMcpSettings = onOpenMcpSettings,
            modifier = Modifier.padding(bottom = JwSpacing.extraSmall),
        )
        JwHorizontalDivider()
        Column(
            modifier = Modifier.padding(vertical = JwSpacing.extraSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(JwSpacing.tiny),
        ) {
            RailSessionButton(
                sessions = sessions,
                selectedSession = selectedSession,
                onSelectSession = onSelectSession,
            )
        }
        JwHorizontalDivider()
        RailPluginList(
            plugins = plugins,
            selectedAppName = selectedSession?.appDisplayName,
            onClickPlugin = onClickPlugin,
            modifier = Modifier.weight(1f),
        )
        JwHorizontalDivider()
        Column(
            modifier = Modifier.padding(vertical = JwSpacing.extraSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(JwSpacing.tiny),
        ) {
            JwIconButton(onClick = onOpenAllMcpTools, tooltip = stringResource(Res.string.mcp_tools_open_all)) {
                JwIcon(imageVector = Icons.Default.Build, contentDescription = null)
            }
            JwIconButton(onClick = onClickSettings, tooltip = stringResource(Res.string.settings)) {
                JwIcon(imageVector = Icons.Default.Settings, contentDescription = null)
            }
            JwIconButton(onClick = onClickInfo, tooltip = stringResource(Res.string.info)) {
                JwIcon(imageVector = Icons.Default.Info, contentDescription = null)
            }
        }
    }
}

/**
 * The enabled plugins as icons, those of the host section first. A plugin listed twice, once for the
 * host session and once for the selected app, says in its label which one each icon opens.
 */
@Composable
private fun RailPluginList(
    plugins: ImmutableList<DrawerPluginItemUiState>,
    selectedAppName: String?,
    onClickPlugin: (DrawerPluginItemUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val railPlugins = remember(plugins) {
        plugins.filter { it.pluginAvailability == PluginAvailability.Enabled }.sortedBy(DrawerPluginItemUiState::needsApp)
    }
    val pluginIdsListedTwice = remember(railPlugins) { railPlugins.groupingBy(DrawerPluginItemUiState::id).eachCount().filterValues { it > 1 }.keys }
    LazyColumn(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = JwSpacing.extraSmall),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.tiny),
    ) {
        items(items = railPlugins, key = DrawerPluginItemUiState::rowKey) {
            val usable = it.sessionId != null
            val selected = it.isSelected && usable
            Box {
                JwIconButton(
                    enabled = usable,
                    selected = selected,
                    onClick = { onClickPlugin(it) },
                    tooltip = when {
                        it.id !in pluginIdsListedTwice -> it.name
                        it.needsApp -> stringResource(Res.string.rail_plugin_in_app, it.name, selectedAppName.orEmpty())
                        else -> stringResource(Res.string.rail_plugin_in_host_session, it.name)
                    },
                ) {
                    JwIcon(
                        painter = when {
                            selected -> rememberPluginIconSvgPainter(it.activeIconResource)
                                ?: painterResource(Res.drawable.puzzle_filled)

                            else -> rememberPluginIconSvgPainter(it.inactiveIconResource)
                                ?: painterResource(Res.drawable.puzzle_outlined)
                        },
                        contentDescription = null,
                    )
                }
                if (it.underAiControl || it.exposesMcpTools) {
                    RailBadge(
                        tone = if (it.underAiControl) JwTone.Warning else JwTone.Neutral,
                        filled = it.underAiControl,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
        }
    }
}

@Composable
private fun RailSessionButton(
    sessions: ImmutableList<DebugSession>,
    selectedSession: DebugSession?,
    onSelectSession: (DebugSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val activeSessions = remember(sessions) { sessions.filter(DebugSession::isActive) }
    Box(modifier = modifier) {
        JwIconButton(
            enabled = activeSessions.isNotEmpty(),
            onClick = { expanded = true },
            tooltip = selectedSession?.deviceAndAppDisplayName ?: stringResource(Res.string.no_session_available),
        ) {
            JwIcon(imageVector = Icons.Default.Devices, contentDescription = null)
        }
        if (selectedSession != null) {
            RailBadge(
                tone = JwTone.Success,
                filled = true,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        JwDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            activeSessions.forEach { session ->
                SessionMenuItem(
                    selected = session.id == selectedSession?.id,
                    session = session,
                    displayName = session.deviceAndAppDisplayName,
                    onClick = {
                        onSelectSession(session)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** How far the badge sits in from the button's corner. */
private val BadgeOffset = 1.dp

/** The rail-colored ring that separates the badge from what it overlaps. */
private val BadgeRingWidth = 1.5f.dp

/**
 * A status dot pinned to a rail button's corner. It sits just outside the 16dp glyph, and a ring in
 * the rail's own color separates it from whatever it overlaps.
 */
@Composable
private fun RailBadge(
    tone: JwTone,
    filled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(BadgeOffset)
            .background(JwTheme.colors.sidebarBackground, CircleShape)
            .padding(BadgeRingWidth),
    ) {
        JwStatusDot(tone = tone, filled = filled)
    }
}

@Composable
fun rememberPluginIconSvgPainter(
    resource: PluginIconResource?,
): Painter? {
    if (resource == null) return null
    val density = LocalDensity.current
    return remember(resource) {
        resource.path.openStream().use { it.readBytes().decodeToSvgPainter(density) }
    }
}

@Preview
@Composable
private fun ShrunkToolingDrawerViewPreview() {
    JwTheme(darkTheme = false) {
        ShrunkToolingDrawerView(
            plugins = persistentListOf(
                DrawerPluginItemUiState(
                    name = "Inspector",
                    id = "com.example.inspector",
                    sessionId = null,
                    activeIconResource = null,
                    inactiveIconResource = null,
                    pluginAvailability = PluginAvailability.Enabled,
                    isSelected = true,
                    underAiControl = false,
                    exposesMcpTools = true,
                    isHeadless = false,
                ),
            ),
            sessions = persistentListOf(),
            selectedSession = null,
            aiActivity = AiActivityUiState.Idle,
            onFollowAiOperationChange = {},
            onOpenMcpSettings = {},
            onClickExpandMenu = {},
            onClickSettings = {},
            onClickPlugin = {},
            onClickInfo = {},
            onOpenAllMcpTools = {},
            onSelectSession = {},
        )
    }
}
