package com.kitakkun.jetwhale.debugger.host.drawer

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.debugger.host.component.ToolingDrawer
import com.kitakkun.jetwhale.debugger.host.model.DebugSession
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolingScaffold(
    uiState: ToolingScaffoldUiState,
    onClickSettings: () -> Unit,
    onClickInfo: () -> Unit,
    onClickPlugin: (String) -> Unit,
    onSelectSession: (DebugSession) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface {
        PermanentNavigationDrawer(
            drawerContent = {
                ToolingDrawer(
                    plugins = uiState.plugins,
                    sessions = uiState.sessions,
                    selectedSession = uiState.selectedSession,
                    selectedPluginId = uiState.selectedPluginId,
                    onClickSettings = onClickSettings,
                    onClickInfo = onClickInfo,
                    onClickPlugin = onClickPlugin,
                    onSelectSession = onSelectSession,
                )
            },
            content = {
                content()
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
        ),
        onClickSettings = {},
        onClickInfo = {},
        onClickPlugin = {},
        onSelectSession = {},
    ) {
        Text("Hello, World!")
    }
}
