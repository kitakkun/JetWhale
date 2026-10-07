package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.McpCallArgument
import com.kitakkun.jetwhale.host.model.McpCallRecord
import com.kitakkun.jetwhale.host.model.McpToolParameterSummary
import com.kitakkun.jetwhale.host.model.McpToolSummary
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.network.host.NetworkHostPluginFactory
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsShot
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsShotRecorder
import com.kitakkun.jetwhale.tools.docsscreenshots.HostWindowSurface
import com.kitakkun.jetwhale.tools.docsscreenshots.InMemoryPluginStorage
import com.kitakkun.jetwhale.tools.docsscreenshots.mouseClickThenMovePointerAway
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

/**
 * The MCP tools browser as the Network Inspector's MCP badge opens it, narrowed to that plugin and
 * the selected app. The tools are the ones the plugin publishes: it is created through its factory,
 * as the host does, and its commands are listed as the MCP server's tool registry lists them.
 */
@OptIn(ExperimentalTestApi::class)
class McpToolsBrowserDocsScreenshots {
    private val recorder = DocsShotRecorder.forImagesDirectoryProperty()

    @Test
    fun `the tools browser with a tool selected`() = recorder.record(
        // The browser takes 80% of the window it opens in, so the window is a quarter wider than it.
        DocsShot(page = "mcp-server", name = "tools-browser", surfaceSize = DpSize(860.dp, 600.dp), density = 2f, displayWidth = 688),
    ) { darkTheme ->
        setContent {
            HostWindowSurface(darkTheme = darkTheme) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    McpToolsScreen(
                        uiState = toolsBrowserUiState(),
                        onSelectPluginFilters = {},
                        onSelectSessionFilters = {},
                        modifier = Modifier.testTag(TOOLS_BROWSER_TAG),
                    )
                }
            }
        }
        onNodeWithText("listTransactions").mouseClickThenMovePointerAway()
        onNodeWithTag(TOOLS_BROWSER_TAG)
    }
}

private const val TOOLS_BROWSER_TAG = "tools-browser"

private const val NETWORK_ID = "com.kitakkun.jetwhale.network"

private const val NETWORK_NAME = "Network Inspector"

@OptIn(ExperimentalJetWhaleApi::class, InternalJetWhaleHostApi::class)
private fun toolsBrowserUiState(): McpToolsScreenUiState {
    // Bound as the host binds a new instance, before its commands are read.
    val pluginScope = CoroutineScope(Job())
    val plugin = NetworkHostPluginFactory().createPlugin()
    plugin.bindPluginScope(pluginScope)
    plugin.bindStorage(InMemoryPluginStorage(emptyMap()))
    val toolRows = (plugin as JetWhaleMcpCapablePlugin).mcpCommands.map { command ->
        McpToolRowUiState(
            pluginId = NETWORK_ID,
            pluginName = NETWORK_NAME,
            tool = command.toToolSummary(),
            callCount = CALLS.count { it.toolName == command.name },
            running = false,
        )
    }
    pluginScope.cancel()
    return McpToolsScreenUiState(
        pluginOptions = persistentListOf(McpFilterOption(id = NETWORK_ID, label = NETWORK_NAME)),
        sessionOptions = sessionFilterOptions(sessions = listOf(SESSION), hostLabel = "Host", disconnectedLabel = "disconnected"),
        selectedPluginIds = persistentSetOf(NETWORK_ID),
        selectedSessionIds = persistentSetOf(SESSION.id),
        toolRows = toolRows.sortedBy { it.tool.name }.toImmutableList(),
        callHistory = CALLS,
        runningToolName = null,
    )
}

/** The command as the MCP server's tool registry lists it: each parameter's JSON Schema type. */
@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.toToolSummary(): McpToolSummary {
    val descriptor = toDescriptor()
    return McpToolSummary(
        name = descriptor.name,
        description = descriptor.description,
        parameters = descriptor.parameters.map { (name, parameter) ->
            McpToolParameterSummary(
                name = name,
                type = (parameter.schema["type"] as? JsonPrimitive)?.content.orEmpty(),
                required = parameter.required,
                description = parameter.description,
            )
        },
    )
}

private val SESSION = DebugSession(
    id = "4b7e21c9-0d5a-4f63-a8e2-6c1f9d3b7a40",
    name = "Sample App",
    isActive = true,
    transportSecurity = SessionTransportSecurity.LOOPBACK,
    installedPlugins = persistentListOf(),
    appName = "Sample App",
    deviceId = "emulator-5554",
    deviceName = "Pixel 9",
)

/** 2026-10-01 09:30 UTC. */
private const val FIRST_CALL_AT = 1_790_847_000_000

/** Newest first, as the browser lists them. */
private val CALLS = persistentListOf(
    McpCallRecord(
        id = 3,
        toolName = "com.kitakkun.jetwhale.network.getTransaction",
        pluginId = NETWORK_ID,
        sessionId = SESSION.id,
        succeeded = true,
        finishedAtEpochMillis = FIRST_CALL_AT + 9_000,
        arguments = persistentListOf(McpCallArgument(name = "txId", value = "tx-2")),
        response = """{"request":{"method":"GET","url":"https://example.com/api/items?page=1&size=20"},"response":{"statusCode":200}}""",
    ),
    McpCallRecord(
        id = 2,
        toolName = "com.kitakkun.jetwhale.network.listTransactions",
        pluginId = NETWORK_ID,
        sessionId = SESSION.id,
        succeeded = true,
        finishedAtEpochMillis = FIRST_CALL_AT + 4_000,
        arguments = persistentListOf(McpCallArgument(name = "limit", value = "20")),
        response = """{"transactions":[{"txId":"tx-2","method":"GET","url":"https://example.com/api/items?page=1&size=20","statusCode":200}]}""",
    ),
)
