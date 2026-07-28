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
}

sealed interface ToolingScaffoldScreenActionResult {
    /** Carries the sessions themselves, since they are gone from the session list by the time this is handled. */
    data class SessionClosed(val closedSessions: ImmutableList<DebugSession>) : ToolingScaffoldScreenActionResult
    data class SetPluginEnabledFailed(val error: Throwable) : ToolingScaffoldScreenActionResult
}

/**
 * The sessions present in [previous] but missing from [current], i.e. the ones that disconnected
 * since the last session-list update.
 */
internal fun closedSessions(
    previous: List<DebugSession>,
    current: List<DebugSession>,
): List<DebugSession> {
    val currentIds = current.mapTo(mutableSetOf()) { it.id }
    return previous.filterNot { it.id in currentIds }
}

@Composable
context(presenterContext: ToolingScaffoldPresenterContext)
fun toolingScaffoldPresenter(
    screenChannel: ScreenChannel<ToolingScaffoldScreenAction, ToolingScaffoldScreenActionResult>,
    loadedPlugins: ImmutableList<PluginMetaData>,
    debugSessions: ImmutableList<DebugSession>,
    enabledPluginIds: Set<String>,
    hasFailedJars: Boolean,
): ToolingScaffoldUiState {
    var selectedSessionId by retain { mutableStateOf("") }
    var selectedPluginId by retain { mutableStateOf("") }
    val selectedSession by remember(debugSessions, selectedSessionId) {
        derivedStateOf { debugSessions.firstOrNull { it.id == selectedSessionId } }
    }

    val setPluginEnabledMutation = rememberMutation(presenterContext.setPluginEnabledMutationKey)

    val plugins by remember(loadedPlugins, selectedSession, enabledPluginIds) {
        derivedStateOf {
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
                )
            }.toImmutableList()
        }
    }

    LaunchedEffect(debugSessions) {
        if (selectedSession == null) {
            selectedSessionId = debugSessions.firstOrNull()?.id.orEmpty()
        }
    }

    // Comparing against the previous session list keeps a disconnect reported exactly once, instead
    // of re-reporting every session that has ever closed on each session-list update.
    var knownSessions by remember { mutableStateOf<List<DebugSession>>(emptyList()) }
    LaunchedEffect(debugSessions) {
        val closedSessions = closedSessions(previous = knownSessions, current = debugSessions)
        knownSessions = debugSessions
        if (closedSessions.isEmpty()) return@LaunchedEffect
        screenChannel.emit(ToolingScaffoldScreenActionResult.SessionClosed(closedSessions.toImmutableList()))
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
    )
}
