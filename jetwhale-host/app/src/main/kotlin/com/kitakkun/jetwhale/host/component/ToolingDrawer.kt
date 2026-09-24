package com.kitakkun.jetwhale.host.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.drawer.AiActivityUiState
import com.kitakkun.jetwhale.host.drawer.DrawerPluginItemUiState
import com.kitakkun.jetwhale.host.drawer.ExpandedToolingDrawerView
import com.kitakkun.jetwhale.host.drawer.ShrunkToolingDrawerView
import com.kitakkun.jetwhale.host.model.DebugSession
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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
    modifier: Modifier = Modifier,
) {
    // Retained rather than remembered: a settings dialog opening over the window must not reset a
    // sidebar the user just collapsed.
    var expandMenu by retain { mutableStateOf(true) }

    AnimatedSwappableContent(
        showContent1 = expandMenu,
        modifier = modifier,
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
                onClickInfo = onClickInfo,
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
                selectedSession = selectedSession,
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

@Preview
@Composable
private fun ToolingDrawerPreview() {
    ToolingDrawer(
        plugins = persistentListOf(),
        hasFailedJars = false,
        sessions = persistentListOf(),
        selectedSession = null,
        selectedPluginId = "",
        aiActivity = AiActivityUiState.Idle,
        onClickSettings = {},
        onClickPluginSettings = {},
        onClickInfo = {},
        onClickPlugin = {},
        onOpenMcpTools = {},
        onOpenAllMcpTools = {},
        onSelectSession = {},
        onClickPopout = {},
        isPoppedOut = { false },
        onClickBringBack = {},
        onSetPluginEnabled = { _, _ -> },
    )
}
