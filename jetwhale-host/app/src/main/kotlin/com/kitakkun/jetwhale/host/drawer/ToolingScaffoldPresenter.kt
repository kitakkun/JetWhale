package com.kitakkun.jetwhale.host.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.architecture.ActionEffect
import com.kitakkun.jetwhale.host.architecture.MutationErrorEffect
import com.kitakkun.jetwhale.host.architecture.ScreenChannel
import com.kitakkun.jetwhale.host.component.rememberAiOperating
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.HeadlessPlugins
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.McpActivity
import com.kitakkun.jetwhale.host.model.McpCapablePlugins
import com.kitakkun.jetwhale.host.model.McpToolInvocation
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.PluginMetaData
import com.kitakkun.jetwhale.host.model.SetPluginEnabledParams
import com.kitakkun.jetwhale.host.model.SidebarWidth
import com.kitakkun.jetwhale.host.ui.JwMetrics
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import soil.query.compose.rememberMutation

sealed interface ToolingScaffoldScreenAction {
    data class SelectSession(val session: DebugSession) : ToolingScaffoldScreenAction

    data class UpdateSelectedPlugin(val pluginId: String) : ToolingScaffoldScreenAction

    data class SetPluginEnabled(val pluginId: String, val enabled: Boolean) : ToolingScaffoldScreenAction

    /** The sidebar's edge was dragged to [width]; kept in memory until [SaveSidebarWidth]. */
    data class ResizeSidebar(val width: Dp) : ToolingScaffoldScreenAction

    /** The drag ended: the width the sidebar has now is stored for the next launch. */
    data object SaveSidebarWidth : ToolingScaffoldScreenAction

    /** Switches the follow mode from the AI indicator, without a trip to the settings. */
    data class SetFollowAiOperation(val enabled: Boolean) : ToolingScaffoldScreenAction
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
    val stillConnected = current.filter(DebugSession::isActive).mapTo(mutableSetOf(), DebugSession::id)
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
    val alreadyConnected = previouslyConnected.mapTo(mutableSetOf(), DebugSession::id)
    return current.filter { it.isActive && it.id !in alreadyConnected }
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
    persistedSidebarWidth: SidebarWidth,
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
    val saveSidebarWidthMutation = rememberMutation(presenterContext.saveSidebarWidthMutationKey)
    // Retained so a settings dialog over the window does not reset a width being dragged; seeded
    // from storage only until the user drags.
    var draggedSidebarWidth by retain { mutableStateOf<Dp?>(null) }
    val sidebarWidth = clampSidebarWidth(draggedSidebarWidth ?: persistedSidebarWidth.widthDp?.dp ?: JwMetrics.sidebarWidth)

    val plugins by remember(loadedPlugins, selectedSession, enabledPluginIds, mcpCapablePlugins, headlessPlugins, activeInvocation) {
        derivedStateOf {
            loadedPlugins.map { metaData ->
                // A plugin that needs no app lives in the host session, whatever app is selected.
                val sessionId = if (metaData.requiresAgent) selectedSession?.id else HostSession.ID
                val isInstalledOnAgent = selectedSession?.installedPlugins?.any { installed -> installed.pluginId == metaData.id } == true
                val isEnabledInSettings = enabledPluginIds.contains(metaData.id)

                DrawerPluginItemUiState(
                    id = metaData.id,
                    name = metaData.name,
                    activeIconResource = metaData.activeIconResource,
                    inactiveIconResource = metaData.inactiveIconResource,
                    pluginAvailability = when {
                        // An app plugin runs only where the selected app's agent advertised it.
                        metaData.requiresAgent && !isInstalledOnAgent -> PluginAvailability.Unavailable

                        isEnabledInSettings -> PluginAvailability.Enabled

                        else -> PluginAvailability.Disabled
                    },
                    // Attributed only when the operation targets the session this row opens in;
                    // highlighting a plugin for some other device would be misleading.
                    underAiControl = activeInvocation?.pluginId == metaData.id && sessionId != null && activeInvocation.sessionId == sessionId,
                    exposesMcpTools = mcpCapablePlugins.toolsFor(sessionId, metaData.id).isNotEmpty(),
                    isHeadless = headlessPlugins.isHeadless(sessionId, metaData.id),
                    needsApp = metaData.requiresAgent,
                )
            }.toImmutableList()
        }
    }

    LaunchedEffect(debugSessions) {
        if (selectedSession?.isActive != true) {
            selectedSessionId = debugSessions.firstOrNull(DebugSession::isActive)?.id.orEmpty()
        }
    }

    // Seeded from the sessions of the first composition so opening the window announces nothing:
    // whoever was already connected is not an arrival. Read only inside the effect below, never
    // during composition, so writing it back cannot drive a recomposition loop.
    var connectedSessions by remember { mutableStateOf(debugSessions.filter(DebugSession::isActive)) }
    LaunchedEffect(debugSessions) {
        val closedSessions = closedSessions(previouslyConnected = connectedSessions, current = debugSessions)
        val connectedSessionsToAnnounce = newlyConnectedSessions(previouslyConnected = connectedSessions, current = debugSessions)
        connectedSessions = debugSessions.filter(DebugSession::isActive)
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

            is ToolingScaffoldScreenAction.SetFollowAiOperation -> {
                followAiOperationMutation.mutateAsync(action.enabled)
            }

            is ToolingScaffoldScreenAction.ResizeSidebar -> {
                draggedSidebarWidth = clampSidebarWidth(action.width)
            }

            is ToolingScaffoldScreenAction.SaveSidebarWidth -> {
                val width = draggedSidebarWidth ?: return@ActionEffect
                saveSidebarWidthMutation.mutateAsync(width.value)
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
            operatingToolShortName = activeInvocation?.shortToolName(),
            operatingPluginName = activeInvocation?.pluginId?.let { pluginId -> loadedPlugins.find { it.id == pluginId }?.name },
            operatingAppName = activeInvocation?.sessionId?.let { sessionId -> debugSessions.find { it.id == sessionId }?.deviceAndAppDisplayName },
            isFollowModeOn = followAiOperationEnabled,
        ),
        sidebarWidth = sidebarWidth,
    )
}

/** Narrow enough to leave the plugin room in a small window, wide enough to read a plugin's name. */
private val MIN_SIDEBAR_WIDTH = 200.dp

/** Past this the sidebar only adds empty space beside short plugin names. */
private val MAX_SIDEBAR_WIDTH = 480.dp

/** [width] kept within what the sidebar can usefully be; a stored width is clamped the same way. */
internal fun clampSidebarWidth(width: Dp): Dp = width.coerceIn(MIN_SIDEBAR_WIDTH, MAX_SIDEBAR_WIDTH)

/**
 * The tool's name without its plugin id's package: `com.kitakkun.jetwhale.mirror.tap` reads as
 * `mirror.tap`. A host tool, which belongs to no plugin, keeps its own short name (`jetwhale.click`).
 */
internal fun McpToolInvocation.shortToolName(): String {
    val pluginId = pluginId ?: return toolName
    if (!toolName.startsWith("$pluginId.")) return toolName
    return "${pluginId.substringAfterLast('.')}.${toolName.removePrefix("$pluginId.")}"
}
