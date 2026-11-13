package com.kitakkun.jetwhale.host.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.kitakkun.jetwhale.host.drawer.AiActivityUiState
import com.kitakkun.jetwhale.host.drawer.DrawerPluginItemUiState
import com.kitakkun.jetwhale.host.drawer.ExpandedToolingDrawerView
import com.kitakkun.jetwhale.host.drawer.ShrunkToolingDrawerView
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.ui.JwMetrics
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun ToolingDrawer(
    plugins: ImmutableList<DrawerPluginItemUiState>,
    hasFailedJars: Boolean,
    sessions: ImmutableList<DebugSession>,
    selectedSession: DebugSession?,
    aiActivity: AiActivityUiState,
    sidebarWidth: Dp,
    onResizeSidebar: (Dp) -> Unit,
    onSidebarResizeFinished: () -> Unit,
    onFollowAiOperationChange: (Boolean) -> Unit,
    onOpenMcpSettings: () -> Unit,
    onClickSettings: () -> Unit,
    onClickPluginSettings: () -> Unit,
    onClickInfo: () -> Unit,
    onClickPlugin: (DrawerPluginItemUiState) -> Unit,
    onClickInactivePlugin: (DrawerPluginItemUiState) -> Unit,
    onOpenMcpTools: (DrawerPluginItemUiState) -> Unit,
    onOpenAllMcpTools: () -> Unit,
    onSelectSession: (DebugSession) -> Unit,
    onClickPopout: (DrawerPluginItemUiState) -> Unit,
    isPoppedOut: (DrawerPluginItemUiState) -> Boolean,
    onClickBringBack: (DrawerPluginItemUiState) -> Unit,
    onSetPluginEnabled: (pluginId: String, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by retain { mutableStateOf(true) }

    AnimatedSwappableContent(
        showContent1 = isExpanded,
        modifier = modifier,
        content1 = {
            ExpandedToolingDrawerView(
                plugins = plugins,
                hasFailedJars = hasFailedJars,
                sessions = sessions,
                selectedSession = selectedSession,
                aiActivity = aiActivity,
                width = sidebarWidth,
                onResize = onResizeSidebar,
                onResizeFinished = onSidebarResizeFinished,
                onFollowAiOperationChange = onFollowAiOperationChange,
                onOpenMcpSettings = onOpenMcpSettings,
                onClickShrinkDrawer = { isExpanded = false },
                onClickSettings = onClickSettings,
                onClickPluginSettings = onClickPluginSettings,
                onClickInfo = onClickInfo,
                onOpenMcpTools = onOpenMcpTools,
                onOpenAllMcpTools = onOpenAllMcpTools,
                onClickPlugin = onClickPlugin,
                onClickInactivePlugin = onClickInactivePlugin,
                onSelectSession = onSelectSession,
                onClickPopout = onClickPopout,
                isPoppedOut = isPoppedOut,
                onClickBringBack = onClickBringBack,
                onSetPluginEnabled = onSetPluginEnabled,
            )
        },
        content2 = {
            ShrunkToolingDrawerView(
                plugins = plugins,
                sessions = sessions,
                selectedSession = selectedSession,
                aiActivity = aiActivity,
                onFollowAiOperationChange = onFollowAiOperationChange,
                onOpenMcpSettings = onOpenMcpSettings,
                onClickPlugin = onClickPlugin,
                onClickExpandMenu = { isExpanded = true },
                onClickSettings = onClickSettings,
                onClickInfo = onClickInfo,
                onOpenAllMcpTools = onOpenAllMcpTools,
                onSelectSession = onSelectSession,
            )
        },
    )
}

@Preview
@Composable
private fun ToolingDrawerPreview() {
    JwTheme(darkTheme = false) {
        ToolingDrawer(
            plugins = persistentListOf(),
            hasFailedJars = false,
            sessions = persistentListOf(),
            selectedSession = null,
            aiActivity = AiActivityUiState.Idle,
            sidebarWidth = JwMetrics.sidebarWidth,
            onResizeSidebar = {},
            onSidebarResizeFinished = {},
            onFollowAiOperationChange = {},
            onOpenMcpSettings = {},
            onClickSettings = {},
            onClickPluginSettings = {},
            onClickInfo = {},
            onClickPlugin = {},
            onClickInactivePlugin = {},
            onOpenMcpTools = {},
            onOpenAllMcpTools = {},
            onSelectSession = {},
            onClickPopout = {},
            isPoppedOut = { false },
            onClickBringBack = {},
            onSetPluginEnabled = { _, _ -> },
        )
    }
}
