package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.McpClientSetup
import com.kitakkun.jetwhale.host.ui.JwMetrics
import com.kitakkun.jetwhale.host.ui.JwSnackbarDuration
import com.kitakkun.jetwhale.host.ui.JwSnackbarHostState
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An MCP client reads the plugin's coordinates in one call and clicks by them in the next, so
 * nothing the host shows about the agent may change the size or place of the plugin.
 */
@OptIn(ExperimentalTestApi::class)
class PluginContentSizeTest {
    @Test
    fun `the plugin keeps its size and place whatever the AI card shows and while the follow notice shows`() = runComposeUiTest {
        var aiActivity by mutableStateOf(AiActivityUiState.Idle)
        val snackbarHostState = JwSnackbarHostState()
        lateinit var scope: CoroutineScope
        setContent {
            scope = rememberCoroutineScope()
            JwTheme(darkTheme = false) {
                ScaffoldAroundPlugin(aiActivity, snackbarHostState)
            }
        }
        val connected = AiActivityUiState.Idle.copy(isAgentConnected = true, isFollowModeOn = true)
        val bounds = mutableListOf<DpRect>()
        fun measure() {
            waitForIdle()
            bounds += onNodeWithTag(PLUGIN_CONTENT).getBoundsInRoot()
        }

        measure()
        aiActivity = AiActivityUiState.Idle.copy(mcpServer = McpServerAvailability.Starting)
        measure()
        aiActivity = AiActivityUiState.Idle.copy(mcpServer = McpServerAvailability.Ready(McpClientSetup.forServer(host = "localhost", port = 7080)))
        measure()
        aiActivity = connected
        measure()
        aiActivity = connected.copy(operatingToolName = "com.example.plugin.tap", operatingToolShortName = "plugin.tap", operatingPluginName = "Plugin")
        measure()
        scope.launch { snackbarHostState.showSnackbar("Following the AI: Plugin", duration = JwSnackbarDuration.Long) }
        waitUntil { onAllNodesWithText("Following the AI: Plugin").fetchSemanticsNodes().isNotEmpty() }
        measure()
        aiActivity = AiActivityUiState.Idle
        measure()

        assertEquals(List(bounds.size) { bounds.first() }, bounds)
    }
}

@Composable
private fun ScaffoldAroundPlugin(aiActivity: AiActivityUiState, snackbarHostState: JwSnackbarHostState) {
    Box(Modifier.size(width = 1200.dp, height = 800.dp)) {
        ToolingScaffold(
            uiState = ToolingScaffoldUiState(
                selectedSessionId = "",
                selectedPluginId = "",
                sessions = persistentListOf(),
                plugins = persistentListOf(),
                hasFailedJars = false,
                aiActivity = aiActivity,
                sidebarWidth = JwMetrics.sidebarWidth,
            ),
            snackbarHostState = snackbarHostState,
            onClickSettings = {},
            onClickPluginSettings = {},
            onClickInfo = {},
            onClickPlugin = {},
            onClickInactivePlugin = {},
            onOpenMcpTools = {},
            onOpenAllMcpTools = {},
            onClickPopout = {},
            isPoppedOut = { false },
            onClickBringBack = {},
            onSelectSession = {},
            onSetPluginEnabled = { _, _ -> },
            onResizeSidebar = {},
            onSidebarResizeFinished = {},
            onFollowAiOperationChange = {},
            onOpenMcpSettings = {},
        ) {
            Box(Modifier.fillMaxSize().testTag(PLUGIN_CONTENT))
        }
    }
}

private const val PLUGIN_CONTENT = "plugin-content"
