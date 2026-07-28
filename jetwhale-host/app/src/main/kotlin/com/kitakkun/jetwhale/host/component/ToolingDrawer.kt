package com.kitakkun.jetwhale.host.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.drawer.AiActivityUiState
import com.kitakkun.jetwhale.host.drawer.DrawerPluginItemUiState
import com.kitakkun.jetwhale.host.drawer.ExpandedToolingDrawerView
import com.kitakkun.jetwhale.host.drawer.ShrunkToolingDrawerView
import com.kitakkun.jetwhale.host.model.DebugSession
import kotlinx.collections.immutable.ImmutableList

@Composable
fun ToolingDrawer(
    plugins: ImmutableList<DrawerPluginItemUiState>,
    hasFailedJars: Boolean,
    sessions: ImmutableList<DebugSession>,
    selectedSession: DebugSession?,
    selectedPluginId: String,
    aiActivity: AiActivityUiState,
    onClickSettings: () -> Unit,
    onClickPluginSettings: () -> Unit,
    onClickInfo: () -> Unit,
    onClickPlugin: (String) -> Unit,
    onOpenMcpTools: (pluginId: String) -> Unit,
    onOpenAllMcpTools: () -> Unit,
    onSelectSession: (DebugSession) -> Unit,
    onClickPopout: (DrawerPluginItemUiState) -> Unit,
    isPoppedOut: (pluginId: String) -> Boolean,
    onClickBringBack: (DrawerPluginItemUiState) -> Unit,
    onSetPluginEnabled: (pluginId: String, enabled: Boolean) -> Unit,
) {
    var expandMenu by remember { mutableStateOf(true) }

    AnimatedSwappableContent(
        showContent1 = expandMenu,
        content1 = {
            ExpandedToolingDrawerView(
                selectedPluginId = selectedPluginId,
                plugins = plugins,
                hasFailedJars = hasFailedJars,
                sessions = sessions,
                selectedSession = selectedSession,
                aiActivity = aiActivity,
                onClickShrinkDrawer = { expandMenu = false },
                onClickSettings = onClickSettings,
                onClickPluginSettings = onClickPluginSettings,
                onOpenMcpTools = onOpenMcpTools,
                onOpenAllMcpTools = onOpenAllMcpTools,
                onClickPlugin = { onClickPlugin(it.id) },
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
                selectedSessionId = selectedSession?.id,
                selectedPluginId = selectedPluginId,
                aiActivity = aiActivity,
                onClickPlugin = onClickPlugin,
                onClickExpandMenu = { expandMenu = true },
                onClickSettings = onClickSettings,
                onClickInfo = onClickInfo,
                onOpenAllMcpTools = onOpenAllMcpTools,
                onSelectSession = onSelectSession,
            )
        },
    )
}
