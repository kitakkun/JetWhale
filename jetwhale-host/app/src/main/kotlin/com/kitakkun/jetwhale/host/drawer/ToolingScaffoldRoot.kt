package com.kitakkun.jetwhale.host.drawer

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.architecture.ActionResultEffect
import com.kitakkun.jetwhale.host.architecture.ScreenChannel
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberScreenChannel
import com.kitakkun.jetwhale.host.following_ai_toast
import com.kitakkun.jetwhale.host.menu.LocalMainWindowMenu
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.HostNavigationRequest
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.PluginInstallJob
import com.kitakkun.jetwhale.host.navigation.toPage
import com.kitakkun.jetwhale.host.plugin_enabled_change_failed_message
import com.kitakkun.jetwhale.host.session_connected_message
import com.kitakkun.jetwhale.host.session_disconnected_message
import com.kitakkun.jetwhale.host.sessions_connected_message
import com.kitakkun.jetwhale.host.sessions_disconnected_message
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import com.kitakkun.jetwhale.host.ui.JwSnackbarDuration
import com.kitakkun.jetwhale.host.ui.JwSnackbarHostState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import soil.query.compose.rememberSubscription

@Composable
context(screenContext: ToolingScaffoldScreenContext)
fun ToolingScaffoldRoot(
    onClickSettings: () -> Unit,
    onClickPluginSettings: () -> Unit,
    onClickInfo: () -> Unit,
    onClickPlugin: (pluginId: String, sessionId: String) -> Unit,
    onClickInactivePlugin: (pluginId: String, pluginName: String, sessionId: String?, notInApp: Boolean) -> Unit,
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
        SoilDataBoundary(
            state1 = rememberSubscription(screenContext.settingsSubscriptionKey),
            state2 = rememberSubscription(screenContext.headlessPluginsSubscriptionKey),
            state3 = rememberSubscription(screenContext.sidebarWidthSubscriptionKey),
            state4 = rememberSubscription(screenContext.mcpServerStatusSubscriptionKey),
            state5 = rememberSubscription(screenContext.pluginInstallJobsSubscriptionKey),
        ) { debuggerSettings, headlessPlugins, persistedSidebarWidth, mcpServerStatus, installJobs ->
            val screenChannel = rememberScreenChannel<ToolingScaffoldScreenAction, ToolingScaffoldScreenActionResult>()
            val snackbarHostState = remember { JwSnackbarHostState() }
            ActionResultEffect(screenChannel) { result ->
                snackbarHostState.showSnackbar(message = result.snackbarMessage(), duration = JwSnackbarDuration.Short)
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
                    persistedSidebarWidth = persistedSidebarWidth,
                    mcpServerStatus = mcpServerStatus,
                )
            }

            SelectionPublishingEffect(
                onSelectedSessionChange = onSelectedSessionChange,
                selectedSession = uiState.selectedSession,
                selectedSessionId = uiState.selectedSessionId,
                selectedPluginId = uiState.selectedPluginId,
            )

            PluginInstallNotices(
                installJobs = installJobs,
                snackbarHostState = snackbarHostState,
                onOpen = { job -> openInstalledPlugin(job, uiState, screenChannel, onClickPlugin, onClickInactivePlugin, onNavigateSettings) },
                onRetry = { request -> screenChannel.send(ToolingScaffoldScreenAction.RetryPluginInstall(request)) },
                onDismiss = { jobIds -> screenChannel.send(ToolingScaffoldScreenAction.DismissPluginInstalls(jobIds)) },
                onShowInstalledPlugins = { onNavigateSettings(SettingsScreenPage.InstalledPlugins) },
                onReviewInstalls = { onNavigateSettings(SettingsScreenPage.AddPlugins) },
            )

            val scope = rememberCoroutineScope()
            HostNavigationRequestEffect(
                screenChannel = screenChannel,
                onFollowAgent = { pluginName ->
                    scope.launch {
                        snackbarHostState.showSnackbar(message = getString(Res.string.following_ai_toast, pluginName), duration = JwSnackbarDuration.Short)
                    }
                },
                onClickPlugin = onClickPlugin,
                onClickInfo = onClickInfo,
                onNavigateHome = onNavigateHome,
                onNavigateSettings = onNavigateSettings,
                onNavigateLogViewer = onNavigateLogViewer,
                sessions = debugSessions,
                uiState = uiState,
            )

            ToolingScaffoldWithActions(
                uiState = uiState,
                screenChannel = screenChannel,
                onClickSettings = onClickSettings,
                onClickPluginSettings = onClickPluginSettings,
                onClickInfo = onClickInfo,
                onClickPlugin = onClickPlugin,
                onClickInactivePlugin = onClickInactivePlugin,
                onOpenMcpTools = onOpenMcpTools,
                onClickPopout = onClickPopout,
                isPoppedOut = isPoppedOut,
                onClickBringBack = onClickBringBack,
                onNavigateSettings = onNavigateSettings,
                snackbarHostState = snackbarHostState,
                content = content,
            )
        }
    }
}

/**
 * The scaffold wired up: every UI event either goes to [screenChannel] as an action or out to the
 * host's navigation callbacks, with [ToolingScaffoldUiState.sessionIdFor] supplying the session id
 * that the scaffold's own callbacks leave out. The plugins the drawer can open are offered in the
 * window's Plugins menu as well.
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
    onClickInactivePlugin: (pluginId: String, pluginName: String, sessionId: String?, notInApp: Boolean) -> Unit,
    onOpenMcpTools: (pluginId: String?, sessionId: String?) -> Unit,
    onClickPopout: (pluginId: String, pluginName: String, sessionId: String) -> Unit,
    isPoppedOut: (pluginId: String, sessionId: String) -> Boolean,
    onClickBringBack: (pluginId: String, sessionId: String) -> Unit,
    onNavigateSettings: (SettingsScreenPage) -> Unit,
    content: @Composable () -> Unit,
) {
    val menu = LocalMainWindowMenu.current
    val hasSelectedApp = uiState.selectedSession != null
    LaunchedEffect(menu, uiState.plugins, hasSelectedApp) {
        menu?.updatePlugins(uiState.plugins, hasSelectedApp)
    }

    ToolingScaffold(
        uiState = uiState,
        onClickSettings = onClickSettings,
        onClickPluginSettings = onClickPluginSettings,
        onClickInfo = onClickInfo,
        onClickPlugin = {
            val sessionId = uiState.sessionIdFor(it) ?: return@ToolingScaffold
            screenChannel.send(ToolingScaffoldScreenAction.UpdateSelectedPlugin(it))
            onClickPlugin(it, sessionId)
        },
        onClickInactivePlugin = {
            screenChannel.send(ToolingScaffoldScreenAction.UpdateSelectedPlugin(it.id))
            onClickInactivePlugin(it.id, it.name, uiState.sessionIdFor(it.id), it.pluginAvailability == PluginAvailability.Unavailable)
        },
        onOpenMcpTools = { onOpenMcpTools(it, uiState.sessionIdFor(it)) },
        onOpenAllMcpTools = { onOpenMcpTools(null, null) },
        onClickPopout = {
            val sessionId = uiState.sessionIdFor(it.id) ?: return@ToolingScaffold
            onClickPopout(it.id, it.name, sessionId)
        },
        isPoppedOut = { pluginId ->
            val sessionId = uiState.sessionIdFor(pluginId) ?: return@ToolingScaffold false
            isPoppedOut(pluginId, sessionId)
        },
        onClickBringBack = {
            val sessionId = uiState.sessionIdFor(it.id) ?: return@ToolingScaffold
            screenChannel.send(ToolingScaffoldScreenAction.UpdateSelectedPlugin(it.id))
            onClickBringBack(it.id, sessionId)
        },
        onSelectSession = { screenChannel.send(ToolingScaffoldScreenAction.SelectSession(it)) },
        onSetPluginEnabled = { pluginId, enabled ->
            screenChannel.send(ToolingScaffoldScreenAction.SetPluginEnabled(pluginId, enabled))
        },
        onFollowAiOperationChange = { enabled ->
            screenChannel.send(ToolingScaffoldScreenAction.SetFollowAiOperation(enabled))
        },
        onOpenMcpSettings = { onNavigateSettings(SettingsScreenPage.McpServer) },
        onResizeSidebar = { screenChannel.send(ToolingScaffoldScreenAction.ResizeSidebar(it)) },
        onSidebarResizeFinished = { screenChannel.send(ToolingScaffoldScreenAction.SaveSidebarWidth) },
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
    uiState: ToolingScaffoldUiState,
    onFollowAgent: (pluginName: String) -> Unit,
    onClickPlugin: (pluginId: String, sessionId: String) -> Unit,
    onClickInfo: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateSettings: (SettingsScreenPage) -> Unit,
    onNavigateLogViewer: () -> Unit,
) {
    val currentSessions by rememberUpdatedState(sessions)
    val currentUiState by rememberUpdatedState(uiState)
    val currentOnClickPlugin by rememberUpdatedState(onClickPlugin)
    val currentOnClickInfo by rememberUpdatedState(onClickInfo)
    val currentOnNavigateHome by rememberUpdatedState(onNavigateHome)
    val currentOnNavigateSettings by rememberUpdatedState(onNavigateSettings)
    val currentOnNavigateLogViewer by rememberUpdatedState(onNavigateLogViewer)
    val currentOnFollowAgent by rememberUpdatedState(onFollowAgent)

    LaunchedEffect(screenChannel) {
        screenContext.hostNavigationService.requests.collect { request ->
            when (request) {
                is HostNavigationRequest.Home -> currentOnNavigateHome()

                is HostNavigationRequest.Info -> currentOnClickInfo()

                is HostNavigationRequest.LogViewer -> currentOnNavigateLogViewer()

                is HostNavigationRequest.Settings -> currentOnNavigateSettings(request.section.toPage())

                is HostNavigationRequest.Plugin -> {
                    val announceFollow = {
                        if (request.followsAgent) {
                            currentOnFollowAgent(currentUiState.plugins.find { it.id == request.pluginId }?.name ?: request.pluginId)
                        }
                    }
                    if (HostSession.isHost(currentUiState.sessionIdFor(request.pluginId))) {
                        screenChannel.send(ToolingScaffoldScreenAction.UpdateSelectedPlugin(request.pluginId))
                        currentOnClickPlugin(request.pluginId, HostSession.ID)
                        announceFollow()
                        return@collect
                    }
                    val targetSession = navigationTargetSession(request.sessionId, currentUiState.selectedSession, currentSessions)
                        ?: return@collect
                    if (targetSession.id != currentUiState.selectedSessionId) {
                        screenChannel.send(ToolingScaffoldScreenAction.SelectSession(targetSession))
                    }
                    screenChannel.send(ToolingScaffoldScreenAction.UpdateSelectedPlugin(request.pluginId))
                    currentOnClickPlugin(request.pluginId, targetSession.id)
                    announceFollow()
                }
            }
        }
    }
}

/**
 * Where Open on an install's notice goes: where a click on the plugin in the drawer goes, judged by
 * the drawer as it is now. A plugin the drawer does not know by id is found in the settings'
 * installed list.
 */
context(screenContext: ToolingScaffoldScreenContext)
private fun openInstalledPlugin(
    job: PluginInstallJob,
    uiState: ToolingScaffoldUiState,
    screenChannel: ScreenChannel<ToolingScaffoldScreenAction, ToolingScaffoldScreenActionResult>,
    onClickPlugin: (pluginId: String, sessionId: String) -> Unit,
    onClickInactivePlugin: (pluginId: String, pluginName: String, sessionId: String?, notInApp: Boolean) -> Unit,
    onNavigateSettings: (SettingsScreenPage) -> Unit,
) {
    val plugin = job.request.pluginId?.let { id -> uiState.plugins.find { it.id == id } }
    if (plugin == null) {
        onNavigateSettings(SettingsScreenPage.InstalledPlugins)
        return
    }
    val sessionId = uiState.sessionIdFor(plugin.id)
    screenChannel.send(ToolingScaffoldScreenAction.UpdateSelectedPlugin(plugin.id))
    if (plugin.pluginAvailability == PluginAvailability.Enabled && sessionId != null) {
        onClickPlugin(plugin.id, sessionId)
    } else {
        onClickInactivePlugin(plugin.id, plugin.name, sessionId, plugin.pluginAvailability == PluginAvailability.Unavailable)
    }
}

/**
 * What the snackbar announces for this result: a session coming or going, or a plugin that could not
 * be enabled or disabled.
 *
 * A single disconnect or arrival names the session; a simultaneous batch (the server stopping, say)
 * is collapsed into a count so the snackbar queue stays short enough to read.
 */
internal suspend fun ToolingScaffoldScreenActionResult.snackbarMessage(): String = when (this) {
    is ToolingScaffoldScreenActionResult.SessionClosed -> closedSessions.singleOrNull()
        ?.let { getString(Res.string.session_disconnected_message, it.deviceAndAppDisplayName) }
        ?: getString(Res.string.sessions_disconnected_message, closedSessions.size)

    is ToolingScaffoldScreenActionResult.SessionConnected -> connectedSessions.singleOrNull()
        ?.let { getString(Res.string.session_connected_message, it.deviceAndAppDisplayName) }
        ?: getString(Res.string.sessions_connected_message, connectedSessions.size)

    is ToolingScaffoldScreenActionResult.SetPluginEnabledFailed -> getString(Res.string.plugin_enabled_change_failed_message, error.message?.takeIf(String::isNotBlank) ?: error::class.simpleName ?: error.javaClass.name)
}

/**
 * The session a plugin navigation request lands on, or null to drop the request. Only a request
 * that named no session falls back to the drawer's selection. A session that has gone away or
 * disconnected drops it — named, or selected but not yet replaced by the presenter: its plugin
 * instances are unloaded, so there is nothing to show, and navigating to some other app instead
 * would surprise the caller.
 */
@VisibleForTesting
internal fun navigationTargetSession(
    requestedSessionId: String?,
    selectedSession: DebugSession?,
    sessions: List<DebugSession>,
): DebugSession? {
    val targetId = requestedSessionId ?: selectedSession?.id ?: return null
    return sessions.firstOrNull { it.id == targetId }?.takeIf(DebugSession::isActive)
}
