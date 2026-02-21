package com.kitakkun.jetwhale.host.drawer

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.component.ToolingDrawer
import com.kitakkun.jetwhale.host.model.DebugSession
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolingScaffold(
    uiState: ToolingScaffoldUiState,
    onClickSettings: () -> Unit,
    onClickInfo: () -> Unit,
    onClickPlugin: (String) -> Unit,
    onClickPopout: (DrawerPluginItemUiState) -> Unit,
    onSelectSession: (DebugSession) -> Unit,
    onSetPluginEnabled: (pluginId: String, enabled: Boolean) -> Unit,
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
                    onClickPopout = onClickPopout,
                    onSetPluginEnabled = onSetPluginEnabled,
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
        onClickPopout = {},
        onSetPluginEnabled = { _, _ -> },
    ) {
        Text("Hello, World!")
    }
}
