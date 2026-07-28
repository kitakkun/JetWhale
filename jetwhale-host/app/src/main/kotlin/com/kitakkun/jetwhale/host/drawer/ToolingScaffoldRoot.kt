package com.kitakkun.jetwhale.host.drawer

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.architecture.ActionResultEffect
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberScreenChannel
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.session_disconnected_message
import com.kitakkun.jetwhale.host.sessions_disconnected_message
import org.jetbrains.compose.resources.getString
import soil.query.compose.rememberSubscription

@Composable
context(screenContext: ToolingScaffoldScreenContext)
fun ToolingScaffoldRoot(
    onClickSettings: () -> Unit,
    onClickPluginSettings: () -> Unit,
    onClickInfo: () -> Unit,
    onClickPlugin: (pluginId: String, sessionId: String) -> Unit,
    onClickPopout: (pluginId: String, pluginName: String, sessionId: String) -> Unit,
    isPoppedOut: (pluginId: String, sessionId: String) -> Boolean,
    onClickBringBack: (pluginId: String, sessionId: String) -> Unit,
    onSelectedSessionChange: (selectedSession: DebugSession) -> Unit,
    content: @Composable () -> Unit,
) {
    SoilDataBoundary(
        state1 = rememberSubscription(screenContext.loadedPluginsMetaDataSubscriptionKey),
        state2 = rememberSubscription(screenContext.debugSessionsSubscriptionKey),
        state3 = rememberSubscription(screenContext.enabledPluginsSubscriptionKey),
        state4 = rememberSubscription(screenContext.failedPluginJarPathsSubscriptionKey),
    ) { loadedPlugins, debugSessions, enabledPluginIds, failedJars ->
        val screenChannel = rememberScreenChannel<ToolingScaffoldScreenAction, ToolingScaffoldScreenActionResult>()
        val snackbarHostState = remember { SnackbarHostState() }
        ActionResultEffect(screenChannel) { result ->
            when (result) {
                is ToolingScaffoldScreenActionResult.SessionClosed -> {
                    // A single disconnect names the session; a simultaneous batch (the server
                    // stopping, say) is collapsed into a count so the queue stays short enough to
                    // read. showSnackbar suspends until dismissed, which serializes the queue.
                    val closedSessions = result.closedSessions
                    val message = closedSessions.singleOrNull()
                        ?.let { getString(Res.string.session_disconnected_message, it.deviceAndAppDisplayName) }
                        ?: getString(Res.string.sessions_disconnected_message, closedSessions.size)
                    snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
                }

                is ToolingScaffoldScreenActionResult.SetPluginEnabledFailed -> Unit
            }
        }
        val uiState = context(screenContext.presenterContext) {
            toolingScaffoldPresenter(
                screenChannel = screenChannel,
                loadedPlugins = loadedPlugins,
                debugSessions = debugSessions,
                enabledPluginIds = enabledPluginIds,
                hasFailedJars = failedJars.isNotEmpty(),
            )
        }

        // When the active/selected session changes, notify the host so that an open plugin
        // screen can follow the newly-selected session instead of lingering on the old one.
        LaunchedEffect(uiState.selectedSessionId) {
            val selectedSession = uiState.selectedSession ?: return@LaunchedEffect
            onSelectedSessionChange(selectedSession)
        }

        ToolingScaffold(
            uiState = uiState,
            onClickSettings = onClickSettings,
            onClickPluginSettings = onClickPluginSettings,
            onClickInfo = onClickInfo,
            onClickPlugin = {
                val selectedSession = uiState.selectedSession ?: return@ToolingScaffold
                screenChannel.send(ToolingScaffoldScreenAction.UpdateSelectedPlugin(it))
                onClickPlugin(it, selectedSession.id)
            },
            onClickPopout = {
                val selectedSession = uiState.selectedSession ?: return@ToolingScaffold
                onClickPopout(it.id, it.name, selectedSession.id)
            },
            isPoppedOut = { pluginId ->
                val selectedSession = uiState.selectedSession ?: return@ToolingScaffold false
                isPoppedOut(pluginId, selectedSession.id)
            },
            onClickBringBack = {
                val selectedSession = uiState.selectedSession ?: return@ToolingScaffold
                screenChannel.send(ToolingScaffoldScreenAction.UpdateSelectedPlugin(it.id))
                onClickBringBack(it.id, selectedSession.id)
            },
            onSelectSession = { screenChannel.send(ToolingScaffoldScreenAction.SelectSession(it)) },
            onSetPluginEnabled = { pluginId, enabled ->
                screenChannel.send(ToolingScaffoldScreenAction.SetPluginEnabled(pluginId, enabled))
            },
            snackbarHostState = snackbarHostState,
            content = content,
        )
    }
}
