package com.kitakkun.jetwhale.host.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.architecture.EventEffect
import com.kitakkun.jetwhale.host.architecture.EventFlow
import com.kitakkun.jetwhale.host.architecture.MutableConsumableEffect
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.PluginMetaData
import io.github.takahirom.rin.rememberRetained
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

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

    val plugins by remember(loadedPlugins, selectedSession) {
        derivedStateOf {
            loadedPlugins.map { metaData ->
                DrawerPluginItemUiState(
                    id = metaData.id,
                    name = metaData.name,
                    activeIconResource = metaData.activeIconResource,
                    inactiveIconResource = metaData.inactiveIconResource,
                    pluginAvailability = selectedSession?.let {
                        // FIXME: Provide version checking logic for plugins
                        //   For now, we just check the plugin ID
                        if (it.installedPlugins.any { installed -> installed.pluginId == metaData.id }) {
                            PluginAvailability.Enabled
                        } else {
                            PluginAvailability.Disabled
                        }
                    } ?: PluginAvailability.Unavailable,
                )
            }.toImmutableList()
        }
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
        plugins = plugins,
    )
}
