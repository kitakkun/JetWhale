package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.app_icon
import com.kitakkun.jetwhale.host.app_short_name
import com.kitakkun.jetwhale.host.bring_back_from_popout
import com.kitakkun.jetwhale.host.collapse_sidebar
import com.kitakkun.jetwhale.host.disable
import com.kitakkun.jetwhale.host.enable
import com.kitakkun.jetwhale.host.info
import com.kitakkun.jetwhale.host.mcp_tools_open_all
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.host.no_app_connected
import com.kitakkun.jetwhale.host.no_plugins_installed
import com.kitakkun.jetwhale.host.plugin_disabled_tooltip
import com.kitakkun.jetwhale.host.plugin_load_error_hint
import com.kitakkun.jetwhale.host.plugin_not_in_app
import com.kitakkun.jetwhale.host.plugins_folded
import com.kitakkun.jetwhale.host.popout
import com.kitakkun.jetwhale.host.puzzle_outlined
import com.kitakkun.jetwhale.host.settings
import com.kitakkun.jetwhale.host.sidebar_unfold
import com.kitakkun.jetwhale.host.theme.LocalEmbeddedInIde
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwMetrics
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwTooltip
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The app mark in the sidebar header: a little larger than a glyph, since it is the brand. */
private val AppMarkSize = 18.dp

/** The puzzle icon of the "no plugins" state. */
private val EmptyStateIconSize = 28.dp

/**
 * The sidebar at full width: a header with the app mark and the collapse control, the plugins that
 * need no app (first, so they stay one click away whatever app is selected), the app picker, the
 * selected app's plugins, and a footer of the host-wide entry points (MCP tools, settings, about).
 */
@Composable
fun ExpandedToolingDrawerView(
    selectedPluginId: String,
    plugins: ImmutableList<DrawerPluginItemUiState>,
    hasFailedJars: Boolean,
    selectedSession: DebugSession?,
    sessions: ImmutableList<DebugSession>,
    aiActivity: AiActivityUiState,
    onClickShrinkDrawer: () -> Unit,
    onClickSettings: () -> Unit,
    onClickPluginSettings: () -> Unit,
    onClickInfo: () -> Unit,
    onOpenMcpTools: (pluginId: String) -> Unit,
    onOpenAllMcpTools: () -> Unit,
    onClickPlugin: (DrawerPluginItemUiState) -> Unit,
    onClickInactivePlugin: (DrawerPluginItemUiState) -> Unit,
    onSelectSession: (DebugSession) -> Unit,
    onClickPopout: (DrawerPluginItemUiState) -> Unit,
    isPoppedOut: (pluginId: String) -> Boolean,
    onClickBringBack: (DrawerPluginItemUiState) -> Unit,
    onSetPluginEnabled: (pluginId: String, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (appPlugins, hostPlugins) = remember(plugins) { plugins.partition(DrawerPluginItemUiState::needsApp) }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(JwMetrics.sidebarWidth)
            .background(JwTheme.colors.sidebarBackground),
    ) {
        SidebarHeader(onClickShrinkDrawer = onClickShrinkDrawer)
        JwHorizontalDivider()
        // The plugins that need no app come first, with no heading: the divider and the app picker
        // below are what set the app's plugins apart.
        if (hostPlugins.isNotEmpty()) {
            PluginList(
                plugins = hostPlugins,
                selectedPluginId = selectedPluginId,
                onOpenMcpTools = onOpenMcpTools,
                onClickPlugin = onClickPlugin,
                onClickInactivePlugin = onClickInactivePlugin,
                onClickPopout = onClickPopout,
                isPoppedOut = isPoppedOut,
                onClickBringBack = onClickBringBack,
                onSetPluginEnabled = onSetPluginEnabled,
            )
            JwHorizontalDivider()
        }
        Column(
            modifier = Modifier.padding(JwSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(JwSpacing.medium),
        ) {
            SessionSelectorView(
                selectedSession = selectedSession,
                sessions = sessions,
                onSelectSession = onSelectSession,
            )
            AiActivityIndicatorView(uiState = aiActivity)
        }
        JwHorizontalDivider()
        when {
            plugins.isEmpty() -> NoPluginsView(
                hasFailedJars = hasFailedJars,
                onClickPluginSettings = onClickPluginSettings,
                modifier = Modifier.weight(1f),
            )

            // With no app selected every app plugin would be greyed, which reads as broken; say what
            // brings them instead.
            selectedSession == null -> JwEmptyState(
                title = stringResource(Res.string.no_app_connected),
                modifier = Modifier.weight(1f),
            )

            else -> PluginList(
                plugins = appPlugins,
                selectedPluginId = selectedPluginId,
                onOpenMcpTools = onOpenMcpTools,
                onClickPlugin = onClickPlugin,
                onClickInactivePlugin = onClickInactivePlugin,
                onClickPopout = onClickPopout,
                isPoppedOut = isPoppedOut,
                onClickBringBack = onClickBringBack,
                onSetPluginEnabled = onSetPluginEnabled,
                modifier = Modifier.weight(1f),
            )
        }
        JwHorizontalDivider()
        SidebarFooter(
            onOpenAllMcpTools = onOpenAllMcpTools,
            onClickSettings = onClickSettings,
            onClickInfo = onClickInfo,
        )
    }
}

@Composable
private fun SidebarHeader(onClickShrinkDrawer: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(JwMetrics.toolbarHeight)
            .padding(start = JwSpacing.large, end = JwSpacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        if (LocalEmbeddedInIde.current) {
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Image(
                painter = painterResource(Res.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier.size(AppMarkSize),
            )
            JwText(
                text = stringResource(Res.string.app_short_name),
                style = JwTheme.textStyles.subtitle,
                modifier = Modifier.weight(1f),
            )
        }
        JwIconButton(
            onClick = onClickShrinkDrawer,
            tooltip = stringResource(Res.string.collapse_sidebar),
        ) {
            JwIcon(
                painter = painterResource(Res.drawable.sidebar_unfold),
                contentDescription = null,
                // The same glyph as the rail's "expand", mirrored: the arrow then points at the
                // edge the sidebar collapses toward.
                modifier = Modifier.scale(scaleX = -1f, scaleY = 1f),
            )
        }
    }
}

@Composable
private fun SidebarFooter(
    onOpenAllMcpTools: () -> Unit,
    onClickSettings: () -> Unit,
    onClickInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(JwMetrics.toolbarHeight)
            .padding(horizontal = JwSpacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.tiny),
    ) {
        // Opens the browser unscoped, so the tools an agent can reach are visible without first
        // finding a plugin that happens to publish some.
        JwIconButton(onClick = onOpenAllMcpTools, tooltip = stringResource(Res.string.mcp_tools_open_all)) {
            JwIcon(imageVector = Icons.Default.Build, contentDescription = null)
        }
        JwIconButton(onClick = onClickSettings, tooltip = stringResource(Res.string.settings)) {
            JwIcon(imageVector = Icons.Default.Settings, contentDescription = null)
        }
        Box(modifier = Modifier.weight(1f))
        JwIconButton(onClick = onClickInfo, tooltip = stringResource(Res.string.info)) {
            JwIcon(imageVector = Icons.Default.Info, contentDescription = null)
        }
    }
}

@Composable
private fun NoPluginsView(
    hasFailedJars: Boolean,
    onClickPluginSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwEmptyState(
        title = stringResource(Res.string.no_plugins_installed),
        icon = {
            JwIcon(
                painter = painterResource(Res.drawable.puzzle_outlined),
                contentDescription = null,
                modifier = Modifier.size(EmptyStateIconSize),
            )
        },
        action = if (hasFailedJars) {
            {
                JwButton(
                    text = stringResource(Res.string.plugin_load_error_hint),
                    onClick = onClickPluginSettings,
                    style = JwButtonStyle.Text,
                    leadingIcon = {
                        JwIcon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = JwTone.Warning.color,
                        )
                    },
                )
            }
        } else {
            null
        },
        modifier = modifier,
    )
}

/** How many greyed-out plugins a list shows before it folds the rest behind one row. */
private const val INACTIVE_PLUGINS_SHOWN_UNFOLDED = 2

/**
 * One area's plugins: the enabled ones, then the rest greyed out in the same list — switched off
 * (enabled again from the row's menu) or not in the selected app (with the reason on hover). A long
 * greyed tail folds behind one row, whose state each area keeps for itself.
 */
@Composable
private fun PluginList(
    plugins: List<DrawerPluginItemUiState>,
    selectedPluginId: String,
    onOpenMcpTools: (pluginId: String) -> Unit,
    onClickPlugin: (DrawerPluginItemUiState) -> Unit,
    onClickInactivePlugin: (DrawerPluginItemUiState) -> Unit,
    onClickPopout: (DrawerPluginItemUiState) -> Unit,
    isPoppedOut: (pluginId: String) -> Boolean,
    onClickBringBack: (DrawerPluginItemUiState) -> Unit,
    onSetPluginEnabled: (pluginId: String, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = PluginActions(
        onOpenMcpTools = onOpenMcpTools,
        onClickPlugin = onClickPlugin,
        onClickInactivePlugin = onClickInactivePlugin,
        onClickPopout = onClickPopout,
        isPoppedOut = isPoppedOut,
        onClickBringBack = onClickBringBack,
        onSetPluginEnabled = onSetPluginEnabled,
    )
    val enabledPlugins = remember(plugins) { plugins.filter { it.pluginAvailability == PluginAvailability.Enabled } }
    val inactivePlugins = remember(plugins) { plugins.filterNot { it.pluginAvailability == PluginAvailability.Enabled } }
    var inactiveExpanded by retain { mutableStateOf(false) }
    val folds = inactivePlugins.size > INACTIVE_PLUGINS_SHOWN_UNFOLDED
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(JwSpacing.extraSmall),
    ) {
        enabledPluginRows(plugins = enabledPlugins, selectedPluginId = selectedPluginId, actions = actions)
        if (folds) {
            item(key = "fold") {
                InactivePluginsFoldRow(
                    count = inactivePlugins.size,
                    expanded = inactiveExpanded,
                    onToggle = { inactiveExpanded = !inactiveExpanded },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (!folds || inactiveExpanded) inactivePluginRows(plugins = inactivePlugins, actions = actions)
    }
}

/**
 * Everything the drawer offers on a plugin, as one value.
 *
 * The lists and the rows they draw for each plugin carry the same set, and the row is the only place
 * any of them is called: passing them one by one made the lists' signatures longer than the bodies
 * that forward them.
 */
private data class PluginActions(
    val onOpenMcpTools: (pluginId: String) -> Unit,
    val onClickPlugin: (DrawerPluginItemUiState) -> Unit,
    val onClickInactivePlugin: (DrawerPluginItemUiState) -> Unit,
    val onClickPopout: (DrawerPluginItemUiState) -> Unit,
    val isPoppedOut: (pluginId: String) -> Boolean,
    val onClickBringBack: (DrawerPluginItemUiState) -> Unit,
    val onSetPluginEnabled: (pluginId: String, enabled: Boolean) -> Unit,
)

private fun LazyListScope.enabledPluginRows(
    plugins: List<DrawerPluginItemUiState>,
    selectedPluginId: String,
    actions: PluginActions,
) {
    items(items = plugins, key = DrawerPluginItemUiState::id) { plugin ->
        PluginDrawerItemView(
            active = true,
            name = plugin.name,
            activeIconResource = plugin.activeIconResource,
            inactiveIconResource = plugin.inactiveIconResource,
            selected = plugin.id == selectedPluginId,
            underAiControl = plugin.underAiControl,
            exposesMcpTools = plugin.exposesMcpTools,
            onClickMcpBadge = { actions.onOpenMcpTools(plugin.id) },
            onClick = { actions.onClickPlugin(plugin) },
            popupMenuContent = { dismiss ->
                JwMenuItem(
                    text = stringResource(Res.string.disable),
                    leadingIcon = { JwIcon(imageVector = Icons.Default.RemoveCircle, contentDescription = null) },
                    onClick = {
                        actions.onSetPluginEnabled(plugin.id, false)
                        dismiss()
                    },
                )
                if (actions.isPoppedOut(plugin.id)) {
                    JwMenuItem(
                        text = stringResource(Res.string.bring_back_from_popout),
                        leadingIcon = { JwIcon(imageVector = Icons.Default.SouthWest, contentDescription = null) },
                        onClick = {
                            actions.onClickBringBack(plugin)
                            dismiss()
                        },
                    )
                } else if (!plugin.isHeadless) {
                    // A window of its own would only carry the "no UI" notice, so a headless
                    // plugin is not offered one.
                    JwMenuItem(
                        text = stringResource(Res.string.popout),
                        leadingIcon = { JwIcon(imageVector = Icons.Default.ArrowOutward, contentDescription = null) },
                        onClick = {
                            actions.onClickPopout(plugin)
                            dismiss()
                        },
                    )
                }
            },
            modifier = Modifier.animateItem(),
        )
    }
}

/**
 * Greyed-out rows. Clicking one opens a screen that says why it can't run: a switched-off plugin can
 * be switched on there as well as from its menu; one the selected app doesn't include offers nothing,
 * since enabling it would change nothing until an app that has it connects.
 */
private fun LazyListScope.inactivePluginRows(
    plugins: List<DrawerPluginItemUiState>,
    actions: PluginActions,
) {
    items(items = plugins, key = DrawerPluginItemUiState::id) { plugin ->
        val notInApp = plugin.pluginAvailability == PluginAvailability.Unavailable
        JwTooltip(
            text = stringResource(if (notInApp) Res.string.plugin_not_in_app else Res.string.plugin_disabled_tooltip),
            modifier = Modifier.animateItem(),
        ) {
            PluginDrawerItemView(
                active = false,
                name = plugin.name,
                activeIconResource = plugin.activeIconResource,
                inactiveIconResource = plugin.inactiveIconResource,
                selected = false,
                underAiControl = plugin.underAiControl,
                exposesMcpTools = plugin.exposesMcpTools,
                onClickMcpBadge = { actions.onOpenMcpTools(plugin.id) },
                onClick = { actions.onClickInactivePlugin(plugin) },
                popupMenuContent = if (notInApp) {
                    null
                } else {
                    { dismiss ->
                        JwMenuItem(
                            text = stringResource(Res.string.enable),
                            leadingIcon = { JwIcon(imageVector = Icons.Default.AddCircle, contentDescription = null) },
                            onClick = {
                                actions.onSetPluginEnabled(plugin.id, true)
                                dismiss()
                            },
                        )
                    }
                },
            )
        }
    }
}

/** The light row that folds a long greyed-out tail away, and brings it back in place. */
@Composable
private fun InactivePluginsFoldRow(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwListItem(
        text = stringResource(Res.string.plugins_folded, count),
        selected = false,
        muted = true,
        onClick = onToggle,
        leadingContent = {
            JwIcon(
                imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = JwTheme.colors.textSecondary,
            )
        },
        modifier = modifier,
    )
}

@Preview
@Composable
private fun ExpandedToolingDrawerViewPreview() {
    val session = DebugSession(
        id = "session-1",
        name = "Sample app",
        isActive = true,
        transportSecurity = SessionTransportSecurity.LOOPBACK,
        installedPlugins = persistentListOf(JetWhalePluginInfo(pluginId = "com.example.inspector", pluginVersion = "1.0.0")),
        appName = "Sample app",
        deviceId = "device-1",
        deviceName = "Pixel 9",
    )
    ExpandedToolingDrawerView(
        selectedPluginId = "com.example.inspector",
        plugins = persistentListOf(
            DrawerPluginItemUiState(
                name = "Device tool",
                id = "com.example.device",
                activeIconResource = null,
                inactiveIconResource = null,
                pluginAvailability = PluginAvailability.Enabled,
                underAiControl = false,
                exposesMcpTools = false,
                isHeadless = false,
                needsApp = false,
            ),
            DrawerPluginItemUiState(
                name = "Inspector",
                id = "com.example.inspector",
                activeIconResource = null,
                inactiveIconResource = null,
                pluginAvailability = PluginAvailability.Enabled,
                underAiControl = false,
                exposesMcpTools = true,
                isHeadless = false,
                needsApp = true,
            ),
            DrawerPluginItemUiState(
                name = "Recorder",
                id = "com.example.recorder",
                activeIconResource = null,
                inactiveIconResource = null,
                pluginAvailability = PluginAvailability.Disabled,
                underAiControl = false,
                exposesMcpTools = false,
                isHeadless = false,
                needsApp = true,
            ),
        ),
        hasFailedJars = false,
        selectedSession = session,
        sessions = persistentListOf(session),
        aiActivity = AiActivityUiState.Idle,
        onClickShrinkDrawer = {},
        onClickSettings = {},
        onClickPluginSettings = {},
        onClickInfo = {},
        onOpenMcpTools = {},
        onOpenAllMcpTools = {},
        onClickPlugin = {},
        onClickInactivePlugin = {},
        onSelectSession = {},
        onClickPopout = {},
        isPoppedOut = { false },
        onClickBringBack = {},
        onSetPluginEnabled = { _, _ -> },
    )
}
