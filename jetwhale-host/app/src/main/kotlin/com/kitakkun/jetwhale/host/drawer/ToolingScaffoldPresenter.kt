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
import com.kitakkun.jetwhale.host.component.rememberAiOperating
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.HeadlessPlugins
import com.kitakkun.jetwhale.host.model.McpActivity
import com.kitakkun.jetwhale.host.model.McpCapablePlugins
import com.kitakkun.jetwhale.host.model.McpToolInvocation
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.PluginMetaData
import com.kitakkun.jetwhale.host.model.SetPluginEnabledParams
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import soil.query.compose.rememberMutation

sealed interface ToolingScaffoldScreenAction {
    data class SelectSession(val session: DebugSession) : ToolingScaffoldScreenAction
    data class UpdateSelectedPlugin(val pluginId: String) : ToolingScaffoldScreenAction
    data class SetPluginEnabled(val pluginId: String, val enabled: Boolean) : ToolingScaffoldScreenAction

    /** Turns the follow mode off from the banner it puts on screen, without a trip to the settings. */
    data object StopFollowingAiOperation : ToolingScaffoldScreenAction
}

sealed interface ToolingScaffoldScreenActionResult {
    /** Carries the sessions themselves, since a handler wants to name what went, not just its id. */
    data class SessionClosed(val closedSessions: ImmutableList<DebugSession>) : ToolingScaffoldScreenActionResult

    /** Carries the sessions themselves, since a handler wants to name what arrived, not just its id. */
    data class SessionConnected(val connectedSessions: ImmutableList<DebugSession>) : ToolingScaffoldScreenActionResult

    data class SetPluginEnabledFailed(val error: Throwable) : ToolingScaffoldScreenActionResult
}

/**
 * The sessions that were connected in [previouslyConnected] and are not any more — either marked
 * inactive or gone from the list entirely.
 *
 * Comparing against the previous snapshot is what keeps a disconnect reported once. Reading the
 * current list alone cannot: a disconnected session stays in it, so every later update would
 * re-report it.
 */
internal fun closedSessions(
    previouslyConnected: List<DebugSession>,
    current: List<DebugSession>,
): List<DebugSession> {
    val stillConnected = current.filter { it.isActive }.mapTo(mutableSetOf()) { it.id }
    return previouslyConnected.filterNot { it.id in stillConnected }
}

/**
 * The sessions that are connected in [current] and were not in [previouslyConnected].
 *
 * "Connected" is what the diff is on, not "present": a disconnected session keeps its entry in the
 * list, so an id reappearing as active is a genuine arrival and is announced — a reconnect included.
 */
internal fun newlyConnectedSessions(
    previouslyConnected: List<DebugSession>,
    current: List<DebugSession>,
): List<DebugSession> {
    val alreadyConnected = previouslyConnected.mapTo(mutableSetOf()) { it.id }
    return current.filter { it.isActive && it.id !in alreadyConnected }
}

/**
 * Whether following this call is something the user would see happen in the main window.
 *
 * Mirrors what [com.kitakkun.jetwhale.host.model.FollowAiOperationService] decides, so the banner
 * announces a move rather than merely an agent being busy. A call that named no session is followed
 * against the drawer's own selection, which is where [selectedSessionId] comes in.
 */
internal fun McpToolInvocation?.movesTheWindow(
    selectedSessionId: String,
    isPluginPoppedOut: (pluginId: String, sessionId: String) -> Boolean,
): Boolean {
    val pluginId = this?.pluginId ?: return false
    val sessionId = this.sessionId ?: selectedSessionId
    return !isPluginPoppedOut(pluginId, sessionId)
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
    headlessPlugins: HeadlessPlugins,
    followAiOperationEnabled: Boolean,
    isPluginPoppedOut: (pluginId: String, sessionId: String) -> Boolean,
): ToolingScaffoldUiState {
    var selectedSessionId by retain { mutableStateOf("") }
    var selectedPluginId by retain { mutableStateOf("") }
    val selectedSession by remember(debugSessions, selectedSessionId) {
        derivedStateOf { debugSessions.firstOrNull { it.id == selectedSessionId } }
    }

    val operating = rememberAiOperating(mcpActivity.startedCount)
    val activeInvocation = mcpActivity.lastStartedInvocation?.takeIf { operating }

    val setPluginEnabledMutation = rememberMutation(presenterContext.setPluginEnabledMutationKey)
    val followAiOperationMutation = rememberMutation(presenterContext.followAiOperationMutationKey)

    val plugins by remember(loadedPlugins, selectedSession, enabledPluginIds, mcpCapablePlugins, headlessPlugins, activeInvocation) {
        derivedStateOf {
            // Attribute the operation only when it targets the session the drawer is showing;
            // highlighting a plugin for some other device would be misleading.
            val aiControlledPluginId = activeInvocation
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
                    exposesMcpTools = mcpCapablePlugins.toolsFor(selectedSession?.id, metaData.id).isNotEmpty(),
                    isHeadless = headlessPlugins.isHeadless(selectedSession?.id, metaData.id),
                )
            }.toImmutableList()
        }
    }

    LaunchedEffect(debugSessions) {
        if (selectedSession?.isActive != true) {
            selectedSessionId = debugSessions.firstOrNull { it.isActive }?.id.orEmpty()
        }
    }

    // Seeded from the sessions of the first composition so opening the window announces nothing:
    // whoever was already connected is not an arrival. Read only inside the effect below, never
    // during composition, so writing it back cannot drive a recomposition loop.
    var connectedSessions by remember { mutableStateOf(debugSessions.filter { it.isActive }) }
    LaunchedEffect(debugSessions) {
        val closedSessions = closedSessions(previouslyConnected = connectedSessions, current = debugSessions)
        val connectedSessionsToAnnounce = newlyConnectedSessions(previouslyConnected = connectedSessions, current = debugSessions)
        connectedSessions = debugSessions.filter { it.isActive }
        if (closedSessions.isNotEmpty()) {
            screenChannel.emit(ToolingScaffoldScreenActionResult.SessionClosed(closedSessions.toImmutableList()))
        }
        if (connectedSessionsToAnnounce.isNotEmpty()) {
            screenChannel.emit(ToolingScaffoldScreenActionResult.SessionConnected(connectedSessionsToAnnounce.toImmutableList()))
        }
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

            ToolingScaffoldScreenAction.StopFollowingAiOperation -> {
                followAiOperationMutation.mutateAsync(false)
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
            // Announce only what the window actually does: a call that names no plugin never moves
            // it, and a plugin popped out into its own window is watched there, not here.
            isFollowingOperation = followAiOperationEnabled && activeInvocation.movesTheWindow(selectedSessionId, isPluginPoppedOut),
        ),
    )
}
