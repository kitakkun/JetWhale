package com.kitakkun.jetwhale.host.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.architecture.ActionResultEffect
import com.kitakkun.jetwhale.host.architecture.ScreenChannel
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberScreenChannel
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.HostNavigationRequest
import com.kitakkun.jetwhale.host.navigation.toPage
import com.kitakkun.jetwhale.host.session_connected_message
import com.kitakkun.jetwhale.host.session_disconnected_message
import com.kitakkun.jetwhale.host.sessions_connected_message
import com.kitakkun.jetwhale.host.sessions_disconnected_message
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import com.kitakkun.jetwhale.host.ui.JwSnackbarDuration
import com.kitakkun.jetwhale.host.ui.JwSnackbarHostState
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.getString
import soil.query.compose.rememberSubscription

@Composable
context(screenContext: ToolingScaffoldScreenContext)
fun ToolingScaffoldRoot(
    onClickSettings: () -> Unit,
    onClickPluginSettings: () -> Unit,
    onClickInfo: () -> Unit,
    onClickPlugin: (pluginId: String, sessionId: String) -> Unit,
    onOpenMcpTools: (pluginId: String?, sessionId: String?) -> Unit,
    onClickPopout: (pluginId: String, pluginName: String, sessionId: String) -> Unit,
    isPoppedOut: (pluginId: String, sessionId: String) -> Boolean,
    onClickBringBack: (pluginId: String, sessionId: String) -> Unit,
    onSelectedSessionChange: (selectedSession: DebugSession) -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateSettings: (SettingsScreenPage) -> Unit,
    onNavigateLogViewer: () -> Unit,
    content: @Composable () -> Unit,
) {
    SoilDataBoundary(
        state1 = rememberSubscription(screenContext.loadedPluginsMetaDataSubscriptionKey),
        state2 = rememberSubscription(screenContext.debugSessionsSubscriptionKey),
        state3 = rememberSubscription(screenContext.enabledPluginsSubscriptionKey),
        state4 = rememberSubscription(screenContext.failedPluginJarPathsSubscriptionKey),
        state5 = rememberSubscription(screenContext.mcpActivitySubscriptionKey),
        state6 = rememberSubscription(screenContext.mcpCapablePluginsSubscriptionKey),
    ) { loadedPlugins, debugSessions, enabledPluginIds, failedJars, mcpActivity, mcpCapablePlugins ->
        // Nested rather than further states: the boundary above is already at the arity it provides,
        // and both reads here are backed by an eagerly-started store, so the extra level resolves in
        // the same frame.
        SoilDataBoundary(
            state1 = rememberSubscription(screenContext.settingsSubscriptionKey),
            state2 = rememberSubscription(screenContext.headlessPluginsSubscriptionKey),
        ) { debuggerSettings, headlessPlugins ->
            val screenChannel = rememberScreenChannel<ToolingScaffoldScreenAction, ToolingScaffoldScreenActionResult>()
            val snackbarHostState = remember { JwSnackbarHostState() }
            ActionResultEffect(screenChannel) { result ->
                // showSnackbar suspends until dismissed, which serializes the queue.
                val message = result.sessionChangeMessage() ?: return@ActionResultEffect
                snackbarHostState.showSnackbar(message = message, duration = JwSnackbarDuration.Short)
            }
            val uiState = context(screenContext.presenterContext) {
                toolingScaffoldPresenter(
                    screenChannel = screenChannel,
                    loadedPlugins = loadedPlugins,
                    debugSessions = debugSessions,
                    enabledPluginIds = enabledPluginIds,
                    hasFailedJars = failedJars.isNotEmpty(),
                    mcpActivity = mcpActivity,
                    mcpCapablePlugins = mcpCapablePlugins,
                    headlessPlugins = headlessPlugins,
                    followAiOperationEnabled = debuggerSettings.followAiOperationEnabled,
                    isPluginPoppedOut = isPoppedOut,
                )
            }

            SelectionPublishingEffect(
                onSelectedSessionChange = onSelectedSessionChange,
                selectedSession = uiState.selectedSession,
                selectedSessionId = uiState.selectedSessionId,
                selectedPluginId = uiState.selectedPluginId,
            )

            HostNavigationRequestEffect(
                screenChannel = screenChannel,
                onClickPlugin = onClickPlugin,
                onClickInfo = onClickInfo,
                onNavigateHome = onNavigateHome,
                onNavigateSettings = onNavigateSettings,
                onNavigateLogViewer = onNavigateLogViewer,
                sessions = debugSessions,
                selectedSession = uiState.selectedSession,
                selectedSessionId = uiState.selectedSessionId,
            )

            ToolingScaffoldWithActions(
                uiState = uiState,
                screenChannel = screenChannel,
                onClickSettings = onClickSettings,
                onClickPluginSettings = onClickPluginSettings,
                onClickInfo = onClickInfo,
                onClickPlugin = onClickPlugin,
                onOpenMcpTools = onOpenMcpTools,
                onClickPopout = onClickPopout,
                isPoppedOut = isPoppedOut,
                onClickBringBack = onClickBringBack,
                snackbarHostState = snackbarHostState,
                content = content,
            )
        }
    }
}

/**
 * The scaffold wired up: every UI event either goes to [screenChannel] as an action or out to the
 * host's navigation callbacks, with the drawer's selected session supplying the session id that the
 * scaffold's own callbacks leave out.
 */
@Composable
context(screenContext: ToolingScaffoldScreenContext)
private fun ToolingScaffoldWithActions(
    uiState: ToolingScaffoldUiState,
    screenChannel: ScreenChannel<ToolingScaffoldScreenAction, ToolingScaffoldScreenActionResult>,
    snackbarHostState: JwSnackbarHostState,
    onClickSettings: () -> Unit,
    onClickPluginSettings: () -> Unit,
    onClickInfo: () -> Unit,
    onClickPlugin: (pluginId: String, sessionId: String) -> Unit,
    onOpenMcpTools: (pluginId: String?, sessionId: String?) -> Unit,
    onClickPopout: (pluginId: String, pluginName: String, sessionId: String) -> Unit,
    isPoppedOut: (pluginId: String, sessionId: String) -> Boolean,
    onClickBringBack: (pluginId: String, sessionId: String) -> Unit,
    content: @Composable () -> Unit,
) {
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
        // The browser tolerates a missing session, so the badge stays usable while no session
        // is selected: it simply opens with the session filter on "All".
        onOpenMcpTools = { onOpenMcpTools(it, uiState.selectedSession?.id) },
        onOpenAllMcpTools = { onOpenMcpTools(null, null) },
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
        onClickStopFollowingAiOperation = {
            screenChannel.send(ToolingScaffoldScreenAction.StopFollowingAiOperation)
        },
        snackbarHostState = snackbarHostState,
        content = content,
    )
}

/**
 * Tells the host which session the drawer points at, so an open plugin screen can follow a session
 * switch, and publishes the selection for callers outside the composition (the MCP server). The
 * destination itself is published by JetWhaleApp.
 */
@Composable
context(screenContext: ToolingScaffoldScreenContext)
private fun SelectionPublishingEffect(
    selectedSession: DebugSession?,
    selectedSessionId: String,
    selectedPluginId: String,
    onSelectedSessionChange: (DebugSession) -> Unit,
) {
    LaunchedEffect(selectedSessionId) {
        onSelectedSessionChange(selectedSession ?: return@LaunchedEffect)
    }

    LaunchedEffect(selectedSessionId, selectedPluginId) {
        screenContext.hostNavigationService.updateSelection(
            selectedSessionId = selectedSessionId.takeIf(String::isNotEmpty),
            selectedPluginId = selectedPluginId.takeIf(String::isNotEmpty),
        )
    }
}

/**
 * The single collector of navigation requests: only here are both the screen channel and the
 * navigation callbacks in scope, and the request channel delivers each request once.
 */
@Composable
context(screenContext: ToolingScaffoldScreenContext)
private fun HostNavigationRequestEffect(
    screenChannel: ScreenChannel<ToolingScaffoldScreenAction, ToolingScaffoldScreenActionResult>,
    sessions: ImmutableList<DebugSession>,
    selectedSession: DebugSession?,
    selectedSessionId: String,
    onClickPlugin: (pluginId: String, sessionId: String) -> Unit,
    onClickInfo: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateSettings: (SettingsScreenPage) -> Unit,
    onNavigateLogViewer: () -> Unit,
) {
    // The collector outlives every recomposition, so it must not close over the sessions, the
    // selection or the callbacks of the composition that started it.
    val currentSessions by rememberUpdatedState(sessions)
    val currentSelectedSession by rememberUpdatedState(selectedSession)
    val currentSelectedSessionId by rememberUpdatedState(selectedSessionId)
    val currentOnClickPlugin by rememberUpdatedState(onClickPlugin)
    val currentOnClickInfo by rememberUpdatedState(onClickInfo)
    val currentOnNavigateHome by rememberUpdatedState(onNavigateHome)
    val currentOnNavigateSettings by rememberUpdatedState(onNavigateSettings)
    val currentOnNavigateLogViewer by rememberUpdatedState(onNavigateLogViewer)

    LaunchedEffect(screenChannel) {
        screenContext.hostNavigationService.requests.collect { request ->
            when (request) {
                is HostNavigationRequest.Home -> currentOnNavigateHome()

                is HostNavigationRequest.Info -> currentOnClickInfo()

                is HostNavigationRequest.LogViewer -> currentOnNavigateLogViewer()

                is HostNavigationRequest.Settings -> currentOnNavigateSettings(request.section.toPage())

                is HostNavigationRequest.Plugin -> {
                    // Only a request that named no session falls back to the drawer's selection.
                    // A named session that has gone away since the request was validated must
                    // drop the request rather than navigate to some other app.
                    val targetSession = when (val requestedSessionId = request.sessionId) {
                        null -> currentSelectedSession
                        else -> currentSessions.firstOrNull { it.id == requestedSessionId }
                    } ?: return@collect
                    // Drive the same path a drawer click takes, so an MCP-driven navigation and a
                    // click are indistinguishable downstream.
                    if (targetSession.id != currentSelectedSessionId) {
                        screenChannel.send(ToolingScaffoldScreenAction.SelectSession(targetSession))
                    }
                    screenChannel.send(ToolingScaffoldScreenAction.UpdateSelectedPlugin(request.pluginId))
                    currentOnClickPlugin(request.pluginId, targetSession.id)
                }
            }
        }
    }
}

/**
 * What to announce for a session coming or going, or null when the result announces nothing.
 *
 * A single disconnect or arrival names the session; a simultaneous batch (the server stopping, say)
 * is collapsed into a count so the snackbar queue stays short enough to read.
 */
private suspend fun ToolingScaffoldScreenActionResult.sessionChangeMessage(): String? = when (this) {
    is ToolingScaffoldScreenActionResult.SessionClosed -> closedSessions.singleOrNull()
        ?.let { getString(Res.string.session_disconnected_message, it.deviceAndAppDisplayName) }
        ?: getString(Res.string.sessions_disconnected_message, closedSessions.size)

    is ToolingScaffoldScreenActionResult.SessionConnected -> connectedSessions.singleOrNull()
        ?.let { getString(Res.string.session_connected_message, it.deviceAndAppDisplayName) }
        ?: getString(Res.string.sessions_connected_message, connectedSessions.size)

    is ToolingScaffoldScreenActionResult.SetPluginEnabledFailed -> null
}
