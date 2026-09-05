package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.component.FollowingAiOperationBanner
import com.kitakkun.jetwhale.host.component.ToolingDrawer
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.ui.JwVerticalDivider
import kotlinx.collections.immutable.persistentListOf

/**
 * The host window: a sidebar that picks the session and the plugin, a hairline, and the selected
 * plugin's own UI filling the rest.
 */
@Composable
fun ToolingScaffold(
    uiState: ToolingScaffoldUiState,
    onClickSettings: () -> Unit,
    onClickPluginSettings: () -> Unit,
    onClickInfo: () -> Unit,
    onClickPlugin: (String) -> Unit,
    onOpenMcpTools: (pluginId: String) -> Unit,
    onOpenAllMcpTools: () -> Unit,
    onClickPopout: (DrawerPluginItemUiState) -> Unit,
    isPoppedOut: (pluginId: String) -> Boolean,
    onClickBringBack: (DrawerPluginItemUiState) -> Unit,
    onSelectSession: (DebugSession) -> Unit,
    onSetPluginEnabled: (pluginId: String, enabled: Boolean) -> Unit,
    onClickStopFollowingAiOperation: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        ToolingDrawer(
            plugins = uiState.plugins,
            hasFailedJars = uiState.hasFailedJars,
            sessions = uiState.sessions,
            selectedSession = uiState.selectedSession,
            selectedPluginId = uiState.selectedPluginId,
            aiActivity = uiState.aiActivity,
            onClickSettings = onClickSettings,
            onClickPluginSettings = onClickPluginSettings,
            onClickInfo = onClickInfo,
            onClickPlugin = onClickPlugin,
            onOpenMcpTools = onOpenMcpTools,
            onOpenAllMcpTools = onOpenAllMcpTools,
            onSelectSession = onSelectSession,
            onClickPopout = onClickPopout,
            isPoppedOut = isPoppedOut,
            onClickBringBack = onClickBringBack,
            onSetPluginEnabled = onSetPluginEnabled,
        )
        JwVerticalDivider()
        Column(modifier = Modifier.fillMaxSize()) {
            // Above the content, not over it: the plugin below is the thing the follow just
            // brought into view, so an overlay would cover what it announces.
            FollowingAiOperationBanner(
                visible = uiState.aiActivity.isFollowingOperation,
                toolName = uiState.aiActivity.operatingToolName.orEmpty(),
                onClickStopFollowing = onClickStopFollowingAiOperation,
            )
            // The snackbar is overlaid on the content area only: messages stay clear of the sidebar
            // and of any popped-out plugin window.
            Box(modifier = Modifier.fillMaxSize()) {
                content()
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
    }
}

@Preview
@Composable
private fun ToolingScaffoldPreview() {
    ToolingScaffold(
        uiState = ToolingScaffoldUiState(
            selectedSessionId = "",
            selectedPluginId = "",
            sessions = persistentListOf(),
            plugins = persistentListOf(),
            hasFailedJars = false,
            aiActivity = AiActivityUiState.Idle,
        ),
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
        onClickStopFollowingAiOperation = {},
        snackbarHostState = remember { SnackbarHostState() },
    ) {
        Text("Hello, World!")
    }
}
