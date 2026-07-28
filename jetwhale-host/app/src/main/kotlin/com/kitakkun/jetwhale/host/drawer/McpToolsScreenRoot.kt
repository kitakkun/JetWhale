package com.kitakkun.jetwhale.host.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.McpActivity
import com.kitakkun.jetwhale.host.model.McpCapablePlugins
import com.kitakkun.jetwhale.host.model.McpToolSummary
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.delay
import soil.query.compose.rememberSubscription
import kotlin.time.Duration.Companion.milliseconds

/** How long a finished MCP tool call keeps reading as "running" on the screen. */
private val MCP_TOOLS_RUNNING_LINGER = 1500.milliseconds

/** One tool row, carrying the plugin that publishes it because the list mixes plugins. */
data class McpToolRowUiState(
    val pluginId: String,
    val pluginName: String,
    val tool: McpToolSummary,
    val callCount: Int,
    val running: Boolean,
) {
    /** Unique across plugins publishing a tool of the same name. */
    val key: String get() = "$pluginId/${tool.name}"
}

/** One value a filter group can be narrowed to. */
data class McpFilterOption(
    val id: String,
    val label: String,
)

@Composable
context(screenContext: McpToolsScreenContext)
fun McpToolsScreenRoot(
    initialPluginId: String?,
    initialSessionId: String?,
) {
    SoilDataBoundary(
        state1 = rememberSubscription(screenContext.loadedPluginsMetaDataSubscriptionKey),
        state2 = rememberSubscription(screenContext.debugSessionsSubscriptionKey),
        state3 = rememberSubscription(screenContext.mcpActivitySubscriptionKey),
        state4 = rememberSubscription(screenContext.mcpCapablePluginsSubscriptionKey),
    ) { loadedPlugins, debugSessions, mcpActivity, mcpCapablePlugins ->
        // An empty set filters nothing, so the nav key's "no preference" maps onto it directly.
        var selectedPluginIds by retain { mutableStateOf(setOfNotNull(initialPluginId).toPersistentSet()) }
        var selectedSessionIds by retain { mutableStateOf(setOfNotNull(initialSessionId).toPersistentSet()) }

        // A call can start and finish between two frames, so watching the running list drops fast
        // calls entirely. Latch on the monotonic counter and hold briefly instead, matching how the
        // drawer decides a plugin is under AI control.
        var running by remember { mutableStateOf(false) }
        LaunchedEffect(mcpActivity.startedCount) {
            if (mcpActivity.startedCount > 0L) {
                running = true
                delay(MCP_TOOLS_RUNNING_LINGER)
                running = false
            }
        }
        val runningInvocation = mcpActivity.lastStartedInvocation?.takeIf { running }

        val pluginNamesById = remember(loadedPlugins) { loadedPlugins.associate { it.id to it.name } }

        McpToolsScreen(
            uiState = rememberMcpToolsUiState(
                mcpCapablePlugins = mcpCapablePlugins,
                mcpActivity = mcpActivity,
                debugSessions = debugSessions,
                pluginNamesById = pluginNamesById,
                selectedPluginIds = selectedPluginIds,
                selectedSessionIds = selectedSessionIds,
                runningPluginId = runningInvocation?.pluginId,
                runningToolName = runningInvocation?.toolName,
            ),
            onSelectPluginFilters = { selectedPluginIds = it.toPersistentSet() },
            onSelectSessionFilters = { selectedSessionIds = it.toPersistentSet() },
        )
    }
}

@Composable
private fun rememberMcpToolsUiState(
    mcpCapablePlugins: McpCapablePlugins,
    mcpActivity: McpActivity,
    debugSessions: ImmutableList<DebugSession>,
    pluginNamesById: Map<String, String>,
    selectedPluginIds: ImmutableSet<String>,
    selectedSessionIds: ImmutableSet<String>,
    runningPluginId: String?,
    runningToolName: String?,
): McpToolsScreenUiState {
    val sessionOptions = remember(debugSessions) {
        debugSessions
            .map { McpFilterOption(id = it.id, label = "${it.deviceDisplayName} · ${it.appDisplayName}") }
            .toImmutableList()
    }

    // Built from every session so the list of plugins does not shift under the user when they narrow
    // the session filter, which would make their own plugin selection disappear.
    val pluginOptions = remember(mcpCapablePlugins, mcpActivity.recentCalls, pluginNamesById) {
        val publishingIds = mcpCapablePlugins.toolsBySessionAndPlugin.values.flatMap { it.keys }
        val calledIds = mcpActivity.recentCalls.mapNotNull { it.pluginId }
        (publishingIds + calledIds)
            .distinct()
            .map { McpFilterOption(id = it, label = pluginNamesById[it] ?: it) }
            .sortedBy { it.label }
            .toImmutableList()
    }

    // Calls that named no session came from a tool that targets none, so they stay visible under a
    // specific session too; hiding them would make a session look quieter than it was.
    val callHistory = remember(mcpActivity.recentCalls, selectedPluginIds, selectedSessionIds) {
        mcpActivity.recentCalls
            .filter { selectedPluginIds.isEmpty() || it.pluginId in selectedPluginIds }
            .filter { selectedSessionIds.isEmpty() || it.sessionId == null || it.sessionId in selectedSessionIds }
            .toImmutableList()
    }

    val toolRows = remember(mcpCapablePlugins, callHistory, pluginNamesById, selectedPluginIds, selectedSessionIds, runningPluginId, runningToolName) {
        // The same plugin publishes the same tools in every session it is active in, so the scope can
        // yield duplicates that carry no extra information.
        val callCounts = callHistory.groupingBy { it.pluginId to it.toolName }.eachCount()
        mcpCapablePlugins.toolsBySessionAndPlugin
            .filterKeys { selectedSessionIds.isEmpty() || it in selectedSessionIds }
            .values
            .flatMap { toolsByPlugin -> toolsByPlugin.entries }
            .filter { selectedPluginIds.isEmpty() || it.key in selectedPluginIds }
            .flatMap { (pluginId, tools) -> tools.map { pluginId to it } }
            .distinctBy { (pluginId, tool) -> "$pluginId/${tool.name}" }
            .map { (pluginId, tool) ->
                McpToolRowUiState(
                    pluginId = pluginId,
                    pluginName = pluginNamesById[pluginId] ?: pluginId,
                    tool = tool,
                    callCount = callCounts[pluginId to tool.name] ?: 0,
                    running = pluginId == runningPluginId && tool.name == runningToolName,
                )
            }
            .sortedWith(compareBy({ it.pluginName }, { it.tool.name }))
            .toImmutableList()
    }

    return McpToolsScreenUiState(
        pluginOptions = pluginOptions,
        sessionOptions = sessionOptions,
        selectedPluginIds = selectedPluginIds,
        selectedSessionIds = selectedSessionIds,
        toolRows = toolRows,
        callHistory = callHistory,
        runningToolName = runningToolName,
    )
}
