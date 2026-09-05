package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.app_icon
import com.kitakkun.jetwhale.host.app_short_name
import com.kitakkun.jetwhale.host.bring_back_from_popout
import com.kitakkun.jetwhale.host.collapse_sidebar
import com.kitakkun.jetwhale.host.disable
import com.kitakkun.jetwhale.host.disabled_plugins
import com.kitakkun.jetwhale.host.enable
import com.kitakkun.jetwhale.host.enabled_plugins
import com.kitakkun.jetwhale.host.info
import com.kitakkun.jetwhale.host.mcp_tools_open_all
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.no_plugins_installed
import com.kitakkun.jetwhale.host.plugin_load_error_hint
import com.kitakkun.jetwhale.host.popout
import com.kitakkun.jetwhale.host.puzzle_outlined
import com.kitakkun.jetwhale.host.settings
import com.kitakkun.jetwhale.host.sidebar_unfold
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwMetrics
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.unavailable_plugins
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The sidebar at full width: a header with the app mark and the collapse control, the session
 * picker, the AI-activity strip, the plugin list grouped by availability, and a footer of the
 * host-wide entry points (MCP tools, settings, about).
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
    onSelectSession: (DebugSession) -> Unit,
    onClickPopout: (DrawerPluginItemUiState) -> Unit,
    isPoppedOut: (pluginId: String) -> Boolean,
    onClickBringBack: (DrawerPluginItemUiState) -> Unit,
    onSetPluginEnabled: (pluginId: String, enabled: Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(JwMetrics.sidebarWidth)
            .background(JwTheme.colors.sidebarBackground),
    ) {
        SidebarHeader(onClickShrinkDrawer = onClickShrinkDrawer)
        JwHorizontalDivider()
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
        if (plugins.isEmpty()) {
            NoPluginsView(
                hasFailedJars = hasFailedJars,
                onClickPluginSettings = onClickPluginSettings,
                modifier = Modifier.weight(1f),
            )
        } else {
            PluginList(
                selectedPluginId = selectedPluginId,
                plugins = plugins,
                onOpenMcpTools = onOpenMcpTools,
                onClickPlugin = onClickPlugin,
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
private fun SidebarHeader(onClickShrinkDrawer: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(JwMetrics.toolbarHeight)
            .padding(start = JwSpacing.large, end = JwSpacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        Image(
            painter = painterResource(Res.drawable.app_icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(Res.string.app_short_name),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
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
) {
    Row(
        modifier = Modifier
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
                modifier = Modifier.size(28.dp),
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

@Composable
private fun PluginList(
    selectedPluginId: String,
    plugins: ImmutableList<DrawerPluginItemUiState>,
    onOpenMcpTools: (pluginId: String) -> Unit,
    onClickPlugin: (DrawerPluginItemUiState) -> Unit,
    onClickPopout: (DrawerPluginItemUiState) -> Unit,
    isPoppedOut: (pluginId: String) -> Boolean,
    onClickBringBack: (DrawerPluginItemUiState) -> Unit,
    onSetPluginEnabled: (pluginId: String, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var enabledPluginsExpanded by retain { mutableStateOf(true) }
    var disabledPluginsExpanded by retain { mutableStateOf(true) }
    var unavailablePluginsExpanded by retain { mutableStateOf(true) }

    val enabledPlugins = remember(plugins) { plugins.filter { it.pluginAvailability == PluginAvailability.Enabled } }
    val disabledPlugins = remember(plugins) { plugins.filter { it.pluginAvailability == PluginAvailability.Disabled } }
    val unavailablePlugins = remember(plugins) { plugins.filter { it.pluginAvailability == PluginAvailability.Unavailable } }
    val enabledTitle = stringResource(Res.string.enabled_plugins)
    val disabledTitle = stringResource(Res.string.disabled_plugins)
    val unavailableTitle = stringResource(Res.string.unavailable_plugins)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(JwSpacing.extraSmall),
    ) {
        pluginSection(
            title = enabledTitle,
            plugins = enabledPlugins,
            expanded = enabledPluginsExpanded,
            onToggleExpanded = { enabledPluginsExpanded = !enabledPluginsExpanded },
        ) { plugin ->
            PluginDrawerItemView(
                enabled = true,
                name = plugin.name,
                activeIconResource = plugin.activeIconResource,
                inactiveIconResource = plugin.inactiveIconResource,
                selected = plugin.id == selectedPluginId,
                underAiControl = plugin.underAiControl,
                exposesMcpTools = plugin.exposesMcpTools,
                onClickMcpBadge = { onOpenMcpTools(plugin.id) },
                onClick = { onClickPlugin(plugin) },
                popupMenuContent = { dismiss ->
                    JwMenuItem(
                        text = stringResource(Res.string.disable),
                        leading = { JwIcon(imageVector = Icons.Default.RemoveCircle, contentDescription = null) },
                        onClick = {
                            onSetPluginEnabled(plugin.id, false)
                            dismiss()
                        },
                    )
                    if (isPoppedOut(plugin.id)) {
                        JwMenuItem(
                            text = stringResource(Res.string.bring_back_from_popout),
                            leading = { JwIcon(imageVector = Icons.Default.SouthWest, contentDescription = null) },
                            onClick = {
                                onClickBringBack(plugin)
                                dismiss()
                            },
                        )
                    } else if (!plugin.isHeadless) {
                        // A window of its own would only carry the "no UI" notice, so a headless
                        // plugin is not offered one.
                        JwMenuItem(
                            text = stringResource(Res.string.popout),
                            leading = { JwIcon(imageVector = Icons.Default.ArrowOutward, contentDescription = null) },
                            onClick = {
                                onClickPopout(plugin)
                                dismiss()
                            },
                        )
                    }
                },
                modifier = Modifier.animateItem(),
            )
        }
        pluginSection(
            title = disabledTitle,
            plugins = disabledPlugins,
            expanded = disabledPluginsExpanded,
            onToggleExpanded = { disabledPluginsExpanded = !disabledPluginsExpanded },
        ) { plugin ->
            PluginDrawerItemView(
                enabled = false,
                name = plugin.name,
                activeIconResource = plugin.activeIconResource,
                inactiveIconResource = plugin.inactiveIconResource,
                selected = false,
                underAiControl = plugin.underAiControl,
                exposesMcpTools = plugin.exposesMcpTools,
                onClickMcpBadge = { onOpenMcpTools(plugin.id) },
                onClick = {
                    // do nothing
                },
                popupMenuContent = { dismiss ->
                    JwMenuItem(
                        text = stringResource(Res.string.enable),
                        leading = { JwIcon(imageVector = Icons.Default.AddCircle, contentDescription = null) },
                        onClick = {
                            onSetPluginEnabled(plugin.id, true)
                            dismiss()
                        },
                    )
                },
                modifier = Modifier.animateItem(),
            )
        }
        pluginSection(
            title = unavailableTitle,
            plugins = unavailablePlugins,
            expanded = unavailablePluginsExpanded,
            onToggleExpanded = { unavailablePluginsExpanded = !unavailablePluginsExpanded },
        ) { plugin ->
            PluginDrawerItemView(
                enabled = false,
                name = plugin.name,
                activeIconResource = plugin.activeIconResource,
                inactiveIconResource = plugin.inactiveIconResource,
                selected = false,
                underAiControl = plugin.underAiControl,
                exposesMcpTools = plugin.exposesMcpTools,
                onClickMcpBadge = { onOpenMcpTools(plugin.id) },
                onClick = {
                    // do nothing
                },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/**
 * One collapsible group of the plugin list. An empty group emits nothing, not even its header.
 */
private fun LazyListScope.pluginSection(
    title: String,
    plugins: List<DrawerPluginItemUiState>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    itemContent: @Composable LazyItemScope.(DrawerPluginItemUiState) -> Unit,
) {
    if (plugins.isEmpty()) return
    item(key = "header:${plugins.first().pluginAvailability}") {
        JwSectionHeader(
            title = title,
            count = plugins.size,
            expanded = expanded,
            onToggleExpanded = onToggleExpanded,
            modifier = Modifier.animateItem(),
        )
    }
    if (!expanded) return
    items(items = plugins, key = { it.id }) { plugin ->
        itemContent(plugin)
    }
}
