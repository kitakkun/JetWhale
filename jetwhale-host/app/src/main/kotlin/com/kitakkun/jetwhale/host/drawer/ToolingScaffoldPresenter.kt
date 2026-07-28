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
private val AI_OPERATION_INDICATOR_LINGER = 700.milliseconds

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

    // Most MCP tool calls complete in a few milliseconds, which would make the indicator flash by
    // unnoticed. Keep the last call on screen for a moment after it finishes so the user can tell
    // that something happened, and which tool did it.
    var lingeringInvocation by remember { mutableStateOf<McpToolInvocation?>(null) }
    LaunchedEffect(mcpActivity.runningInvocations) {
        val running = mcpActivity.runningInvocations.lastOrNull()
        if (running != null) {
            lingeringInvocation = running
        } else if (lingeringInvocation != null) {
            delay(AI_OPERATION_INDICATOR_LINGER)
            lingeringInvocation = null
        }
    }

    val setPluginEnabledMutation = rememberMutation(presenterContext.setPluginEnabledMutationKey)

    val plugins by remember(loadedPlugins, selectedSession, enabledPluginIds, mcpCapablePlugins) {
        derivedStateOf {
            // Attribute the operation only when it targets the session the drawer is showing;
            // highlighting a plugin for some other device would be misleading.
            val mcpCapablePluginIds = mcpCapablePlugins.pluginIdsFor(selectedSession?.id)
            val aiControlledPluginId = lingeringInvocation
                ?.takeIf { it.sessionId != null && it.sessionId == selectedSession?.id }
                ?.pluginId

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
                    exposesMcpTools = metaData.id in mcpCapablePluginIds,
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
            operatingToolName = lingeringInvocation?.toolName,
        ),
    )
}
