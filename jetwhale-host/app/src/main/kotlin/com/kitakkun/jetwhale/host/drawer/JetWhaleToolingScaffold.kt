package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.component.ToolingDrawer
import com.kitakkun.jetwhale.host.model.DebugSession
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolingScaffold(
    uiState: ToolingScaffoldUiState,
    onClickSettings: () -> Unit,
    onClickPluginSettings: () -> Unit,
    onClickInfo: () -> Unit,
    onClickPlugin: (String) -> Unit,
    onClickPopout: (DrawerPluginItemUiState) -> Unit,
    isPoppedOut: (pluginId: String) -> Boolean,
    onClickBringBack: (DrawerPluginItemUiState) -> Unit,
    onSelectSession: (DebugSession) -> Unit,
    onSetPluginEnabled: (pluginId: String, enabled: Boolean) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface {
        PermanentNavigationDrawer(
            drawerContent = {
                ToolingDrawer(
                    plugins = uiState.plugins,
                    hasFailedJars = uiState.hasFailedJars,
                    sessions = uiState.sessions,
                    selectedSession = uiState.selectedSession,
                    selectedPluginId = uiState.selectedPluginId,
                    onClickSettings = onClickSettings,
                    onClickPluginSettings = onClickPluginSettings,
                    onClickInfo = onClickInfo,
                    onClickPlugin = onClickPlugin,
                    onSelectSession = onSelectSession,
                    onClickPopout = onClickPopout,
                    isPoppedOut = isPoppedOut,
                    onClickBringBack = onClickBringBack,
                    onSetPluginEnabled = onSetPluginEnabled,
                )
            },
            content = {
                // The drawer is not a Scaffold, so the snackbar is overlaid on the content area
                // only: messages stay clear of the drawer and of any popped-out plugin window.
                Box(modifier = Modifier.fillMaxSize()) {
                    content()
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    )
                }
            },
            modifier = modifier,
        )
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
        ),
        onClickSettings = {},
        onClickPluginSettings = {},
        onClickInfo = {},
        onClickPlugin = {},
        onSelectSession = {},
        onClickPopout = {},
        isPoppedOut = { false },
        onClickBringBack = {},
        onSetPluginEnabled = { _, _ -> },
        snackbarHostState = remember { SnackbarHostState() },
    ) {
        Text("Hello, World!")
    }
}
