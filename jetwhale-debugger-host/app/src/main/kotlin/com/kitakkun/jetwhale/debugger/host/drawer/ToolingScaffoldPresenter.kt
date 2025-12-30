package com.kitakkun.jetwhale.debugger.host.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.debugger.host.architecture.EventEffect
import com.kitakkun.jetwhale.debugger.host.architecture.EventFlow
import com.kitakkun.jetwhale.debugger.host.architecture.MutableConsumableEffect
import com.kitakkun.jetwhale.debugger.host.model.DebugSession
import com.kitakkun.jetwhale.debugger.host.model.PluginMetaData
import io.github.takahirom.rin.rememberRetained
import kotlinx.collections.immutable.ImmutableList

sealed interface ToolingScaffoldEvent {
    data class SelectSession(val session: DebugSession) : ToolingScaffoldEvent
    data class UpdateSelectedPlugin(val pluginId: String) : ToolingScaffoldEvent
}

sealed interface ToolingScaffoldEffect {
    data class SessionClosed(val closedSessionIds: List<String>) : ToolingScaffoldEffect
}

@Composable
fun toolingScaffoldPresenter(
    eventFlow: EventFlow<ToolingScaffoldEvent>,
    loadedPlugins: ImmutableList<PluginMetaData>,
    debugSessions: ImmutableList<DebugSession>,
): ToolingScaffoldUiState {
    var selectedSessionId by rememberRetained { mutableStateOf("") }
    var selectedPluginId by rememberRetained { mutableStateOf("") }
    val selectedSession by remember(debugSessions, selectedSessionId) {
        derivedStateOf { debugSessions.firstOrNull { it.id == selectedSessionId } }
    }

    val consumableEffect = rememberRetained { MutableConsumableEffect<ToolingScaffoldEffect>() }

    LaunchedEffect(debugSessions) {
        if (selectedSession?.isActive != true) {
            selectedSessionId = debugSessions.firstOrNull { it.isActive }?.id.orEmpty()
        }
    }

    LaunchedEffect(debugSessions) {
        val inactiveSessionIds = debugSessions.filterNot { it.isActive }.map { it.id }
        if (inactiveSessionIds.isEmpty()) return@LaunchedEffect
        consumableEffect.enqueue(ToolingScaffoldEffect.SessionClosed(inactiveSessionIds))
    }

    EventEffect(eventFlow) { event ->
        when (event) {
            is ToolingScaffoldEvent.SelectSession -> {
                selectedSessionId = event.session.id
            }

            is ToolingScaffoldEvent.UpdateSelectedPlugin -> {
                selectedPluginId = event.pluginId
            }
        }
    }

    return ToolingScaffoldUiState(
        selectedSessionId = selectedSessionId,
        selectedPluginId = selectedPluginId,
        sessions = debugSessions,
        plugins = loadedPlugins,
    )
}