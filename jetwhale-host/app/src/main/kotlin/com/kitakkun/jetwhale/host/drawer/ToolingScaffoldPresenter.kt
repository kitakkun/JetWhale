package com.kitakkun.jetwhale.host.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.architecture.ActionEffect
import com.kitakkun.jetwhale.host.architecture.MutationErrorEffect
import com.kitakkun.jetwhale.host.architecture.ScreenChannel
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.McpActivity
import com.kitakkun.jetwhale.host.model.McpCapablePlugins
import com.kitakkun.jetwhale.host.model.McpToolInvocation
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.PluginMetaData
import com.kitakkun.jetwhale.host.model.SetPluginEnabledParams
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import soil.query.compose.rememberMutation
import kotlin.time.Duration.Companion.milliseconds

/** How long an MCP tool call keeps showing after it completes. */
private val AI_OPERATION_INDICATOR_LINGER = 1500.milliseconds

sealed interface ToolingScaffoldScreenAction {
    data class SelectSession(val session: DebugSession) : ToolingScaffoldScreenAction
    data class UpdateSelectedPlugin(val pluginId: String) : ToolingScaffoldScreenAction
    data class SetPluginEnabled(val pluginId: String, val enabled: Boolean) : ToolingScaffoldScreenAction
}

sealed interface ToolingScaffoldScreenActionResult {
    data class SessionClosed(val closedSessionIds: List<String>) : ToolingScaffoldScreenActionResult
    data class SetPluginEnabledFailed(val error: Throwable) : ToolingScaffoldScreenActionResult
}

@Composable
context(presenterContext: ToolingScaffoldPresenterContext)
fun toolingScaffoldPresenter(
    screenChannel: ScreenChannel<ToolingScaffoldScreenAction, ToolingScaffoldScreenActionResult>,
    loadedPlugins: ImmutableList<PluginMetaData>,
    debugSessions: ImmutableList<DebugSession>,
    enabledPluginIds: Set<String>,
    hasFailedJars: Boolean,
    mcpActivity: McpActivity,
    mcpCapablePlugins: McpCapablePlugins,
): ToolingScaffoldUiState {
    var selectedSessionId by retain { mutableStateOf("") }
    var selectedPluginId by retain { mutableStateOf("") }
    val selectedSession by remember(debugSessions, selectedSessionId) {
        derivedStateOf { debugSessions.firstOrNull { it.id == selectedSessionId } }
    }

    // A tool call can start and finish faster than the UI samples runningInvocations, so watching
    // that list drops fast calls entirely. Latch "operating" on whenever startedCount changes and
    // hold it briefly instead: a fast call still registers, and a burst reads as one continuous
    // operation because each new call restarts the hold. Keying the effect on the monotonic counter
    // means a skipped intermediate value still re-fires, since the value differs across frames.
    var operating by remember { mutableStateOf(false) }
    LaunchedEffect(mcpActivity.startedCount) {
        if (mcpActivity.startedCount > 0L) {
            operating = true
            delay(AI_OPERATION_INDICATOR_LINGER)
            operating = false
        }
    }
    val activeInvocation = mcpActivity.lastStartedInvocation?.takeIf { operating }

    val setPluginEnabledMutation = rememberMutation(presenterContext.setPluginEnabledMutationKey)

    val recentCalls = mcpActivity.recentCalls
    val plugins by remember(loadedPlugins, selectedSession, enabledPluginIds, mcpCapablePlugins, activeInvocation, recentCalls) {
        derivedStateOf {
            // Attribute the operation only when it targets the session the drawer is showing;
            // highlighting a plugin for some other device would be misleading.
            val aiControlledPluginId = activeInvocation
                ?.takeIf { it.sessionId != null && it.sessionId == selectedSession?.id }
                ?.pluginId

            // Calls that named no session came from a tool that does not target one, so they belong
            // to whichever session is on screen; the rest are shown only under their own session.
            val callsForSelectedSession = recentCalls.filter {
                it.sessionId == null || it.sessionId == selectedSession?.id
            }

            loadedPlugins.map { metaData ->
                val isInstalledOnAgent = selectedSession?.installedPlugins?.any { installed -> installed.pluginId == metaData.id } == true
                val isEnabledInSettings = enabledPluginIds.contains(metaData.id)

                DrawerPluginItemUiState(
                    id = metaData.id,
                    name = metaData.name,
                    activeIconResource = metaData.activeIconResource,
                    inactiveIconResource = metaData.inactiveIconResource,
                    pluginAvailability = when {
                        selectedSession == null -> PluginAvailability.Unavailable

                        // Host-only plugins (no agent) are available for any active session; agent-backed
                        // plugins are only available where the session's agent advertised them.
                        metaData.requiresAgent && !isInstalledOnAgent -> PluginAvailability.Unavailable

                        isEnabledInSettings -> PluginAvailability.Enabled

                        else -> PluginAvailability.Disabled
                    },
                    underAiControl = aiControlledPluginId == metaData.id,
                    mcpTools = mcpCapablePlugins.toolsFor(selectedSession?.id, metaData.id).toImmutableList(),
                    mcpCallHistory = callsForSelectedSession
                        .filter { it.pluginId == metaData.id }
                        .toImmutableList(),
                    runningMcpToolName = activeInvocation
                        ?.takeIf { it.pluginId == metaData.id }
                        ?.toolName,
                )
            }.toImmutableList()
        }
    }

    LaunchedEffect(debugSessions) {
        if (selectedSession?.isActive != true) {
            selectedSessionId = debugSessions.firstOrNull { it.isActive }?.id.orEmpty()
        }
    }

    LaunchedEffect(debugSessions) {
        val inactiveSessionIds = debugSessions.filterNot { it.isActive }.map { it.id }
        if (inactiveSessionIds.isEmpty()) return@LaunchedEffect
        screenChannel.emit(ToolingScaffoldScreenActionResult.SessionClosed(inactiveSessionIds))
    }

    ActionEffect(screenChannel) { action ->
        when (action) {
            is ToolingScaffoldScreenAction.SelectSession -> {
                selectedSessionId = action.session.id
            }

            is ToolingScaffoldScreenAction.UpdateSelectedPlugin -> {
                selectedPluginId = action.pluginId
            }

            is ToolingScaffoldScreenAction.SetPluginEnabled -> {
                setPluginEnabledMutation.mutateAsync(SetPluginEnabledParams(action.pluginId, action.enabled))
            }
        }
    }

    MutationErrorEffect(setPluginEnabledMutation) { error ->
        screenChannel.emit(ToolingScaffoldScreenActionResult.SetPluginEnabledFailed(error))
    }

    return ToolingScaffoldUiState(
        selectedSessionId = selectedSessionId,
        selectedPluginId = selectedPluginId,
        sessions = debugSessions,
        plugins = plugins,
        hasFailedJars = hasFailedJars,
        aiActivity = AiActivityUiState(
            isAgentConnected = mcpActivity.hasConnectedClient,
            operatingToolName = activeInvocation?.toolName,
        ),
    )
}
