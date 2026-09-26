package com.kitakkun.jetwhale.host

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.SoilFallbackDefaults
import com.kitakkun.jetwhale.host.component.PluginJarArrivalBanner
import com.kitakkun.jetwhale.host.component.UpdateAvailableBanner
import com.kitakkun.jetwhale.host.di.JetWhaleAppGraph
import com.kitakkun.jetwhale.host.drawer.ToolingScaffoldRoot
import com.kitakkun.jetwhale.host.model.AppLanguage
import com.kitakkun.jetwhale.host.model.JetWhaleColorScheme
import com.kitakkun.jetwhale.host.model.PostponeArrivedPluginJarRequest
import com.kitakkun.jetwhale.host.model.TrustPluginRequest
import com.kitakkun.jetwhale.host.model.UpdateCheckResult
import com.kitakkun.jetwhale.host.navigation.DisabledPluginNavKey
import com.kitakkun.jetwhale.host.navigation.EmptyPluginNavKey
import com.kitakkun.jetwhale.host.navigation.InfoNavKey
import com.kitakkun.jetwhale.host.navigation.JetWhaleNavDisplay
import com.kitakkun.jetwhale.host.navigation.LicensesNavKey
import com.kitakkun.jetwhale.host.navigation.LogViewerNavKey
import com.kitakkun.jetwhale.host.navigation.PluginNavKey
import com.kitakkun.jetwhale.host.navigation.PluginPopoutNavKey
import com.kitakkun.jetwhale.host.navigation.SettingsNavKey
import com.kitakkun.jetwhale.host.navigation.addSingleTop
import com.kitakkun.jetwhale.host.navigation.bringPluginBackToMainWindow
import com.kitakkun.jetwhale.host.navigation.followPluginToSession
import com.kitakkun.jetwhale.host.navigation.isPluginPoppedOut
import com.kitakkun.jetwhale.host.navigation.openMcpTools
import com.kitakkun.jetwhale.host.navigation.removeAppPluginEntries
import com.kitakkun.jetwhale.host.navigation.toHostDestination
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import com.kitakkun.jetwhale.host.theme.AppEnvironment
import com.kitakkun.jetwhale.host.theme.HostTheme
import com.kitakkun.jetwhale.host.theme.clearFocusOnBlankPress
import com.kitakkun.jetwhale.host.ui.JwSurface
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import kotlinx.serialization.modules.SerializersModule
import soil.query.compose.SwrClientProvider
import soil.query.compose.rememberMutation
import soil.query.compose.rememberSubscription

// The window's entry point takes its whole dependency graph as a context parameter, which a
// @Preview has no way to build.
@Suppress("KOTRAIL_COMPOSABLE_WITHOUT_PREVIEW")
@Composable
context(appGraph: JetWhaleAppGraph)
fun JetWhaleApp() {
    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class, EmptyPluginNavKey::class, EmptyPluginNavKey.serializer())
                polymorphic(NavKey::class, SettingsNavKey::class, SettingsNavKey.serializer())
                polymorphic(NavKey::class, InfoNavKey::class, InfoNavKey.serializer())
                polymorphic(NavKey::class, PluginNavKey::class, PluginNavKey.serializer())
                polymorphic(NavKey::class, DisabledPluginNavKey::class, DisabledPluginNavKey.serializer())
                polymorphic(NavKey::class, LicensesNavKey::class, LicensesNavKey.serializer())
            }
        },
        EmptyPluginNavKey,
    )

    HostWindowEffects(backStack)

    KeyboardShortcutHandlerProvider(
        onPressSettingsShortcut = { backStack.addSingleTop(SettingsNavKey()) },
    ) {
        SwrClientProvider(appGraph.swrClient) {
            // Startup update check: notify-only. Installing always requires an explicit
            // user action in the settings screen.
            val updateCheckMutation = rememberMutation(appGraph.updateCheckMutationKey)
            var updateBannerDismissed by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (appGraph.debuggerSettingsRepository.readCheckForUpdatesOnStartup()) {
                    runCatching { updateCheckMutation.mutateAsync(Unit) }
                }
            }
            val availableUpdate = updateCheckMutation.data?.takeIf(UpdateCheckResult::updateAvailable)

            SoilDataBoundary(
                state1 = rememberSubscription(appGraph.themeSubscriptionKey),
                state2 = rememberSubscription(appGraph.appearanceSettingsSubscriptionKey),
                fallback = SoilFallbackDefaults.none(),
            ) { theme, settings ->
                ThemedHostWindow(
                    colorScheme = theme.colorScheme,
                    appLanguage = settings.appLanguage,
                    backStack = backStack,
                    availableUpdate = availableUpdate,
                    onDismissUpdateBanner = { updateBannerDismissed = true },
                    isUpdateBannerDismissed = updateBannerDismissed,
                )
            }
        }
    }
}

/**
 * The window's long-lived collectors: what the window shows, what the agent asks it to follow, and
 * the back stack entries that must go when the thing behind them disappears.
 */
@Composable
context(appGraph: JetWhaleAppGraph)
private fun HostWindowEffects(backStack: NavBackStack<NavKey>) {
    // Scenes created for a caller that never displays them (the MCP screenshot tool, say) would
    // otherwise lay out at density 1.0 and disagree with what this window shows.
    val density = LocalDensity.current
    LaunchedEffect(density) {
        appGraph.pluginComposeSceneService.updateHostDensity(density)
    }

    // Publish what the window shows so the MCP server can report it and confirm its own navigation
    // requests were applied. ToolingScaffoldRoot publishes the drawer selection alongside it.
    LaunchedEffect(backStack) {
        snapshotFlow { backStack.toList() }.collect { keys ->
            appGraph.hostNavigationService.updateDestination(keys.toHostDestination())
        }
    }

    // Started from the window rather than at app start: it drives the navigation this window owns,
    // and a headless run has nothing to point anywhere.
    LaunchedEffect(Unit) {
        appGraph.followAiOperationService.followAiOperations()
    }

    LaunchedEffect(Unit) {
        appGraph.debugWebSocketServer.serverStoppedFlow.collect {
            backStack.removeAppPluginEntries()
            appGraph.pluginComposeSceneService.disposeAppSessionPluginScenes()
        }
    }

    LaunchedEffect(backStack) {
        appGraph.debugWebSocketServer.sessionClosedFlow.collect {
            backStack.removeAll { navKey ->
                when (navKey) {
                    is PluginNavKey -> navKey.sessionId == it
                    is DisabledPluginNavKey -> navKey.sessionId == it
                    else -> false
                }
            }
            // Not done inside debugWebSocketServer itself: that would be a dependency cycle.
            appGraph.pluginComposeSceneService.disposePluginSceneForSession(it)
        }
    }

    LaunchedEffect(backStack) {
        appGraph.enabledPluginsRepository.disabledPluginIdFlow.collect { disabledPluginId ->
            backStack.removeAll { navKey ->
                when (navKey) {
                    is PluginNavKey -> navKey.pluginId == disabledPluginId
                    is PluginPopoutNavKey -> navKey.pluginId == disabledPluginId
                    else -> false
                }
            }

            appGraph.pluginComposeSceneService.disposePluginScenesForPlugin(disabledPluginId)
        }
    }
}

@Composable
context(appGraph: JetWhaleAppGraph)
private fun ThemedHostWindow(
    colorScheme: JetWhaleColorScheme,
    appLanguage: AppLanguage,
    backStack: NavBackStack<NavKey>,
    availableUpdate: UpdateCheckResult?,
    isUpdateBannerDismissed: Boolean,
    onDismissUpdateBanner: () -> Unit,
) {
    HostTheme(colorScheme) {
        AppEnvironment(appLanguage) {
            JwSurface(modifier = Modifier.fillMaxSize().clearFocusOnBlankPress()) {
                context(retain { appGraph.toolingScaffoldScreenContext }) {
                    ToolingScaffoldRoot(
                        onClickSettings = { backStack.addSingleTop(SettingsNavKey()) },
                        onClickPluginSettings = {
                            backStack.addSingleTop(
                                SettingsNavKey(initialPage = SettingsScreenPage.InstalledPlugins),
                            )
                        },
                        onClickInfo = { backStack.addSingleTop(InfoNavKey) },
                        onClickInactivePlugin = { pluginId, pluginName, sessionId, notInApp ->
                            backStack.addSingleTop(DisabledPluginNavKey(pluginId, pluginName, sessionId, notInApp))
                        },
                        onClickPlugin = { pluginId, sessionId ->
                            backStack.addSingleTop(PluginNavKey(pluginId, sessionId))
                        },
                        onOpenMcpTools = backStack::openMcpTools,
                        onClickPopout = { pluginId, pluginName, sessionId ->
                            backStack.addSingleTop(
                                PluginPopoutNavKey(
                                    pluginId = pluginId,
                                    sessionId = sessionId,
                                    pluginName = pluginName,
                                ),
                            )
                        },
                        isPoppedOut = backStack::isPluginPoppedOut,
                        onClickBringBack = backStack::bringPluginBackToMainWindow,
                        onNavigateHome = {
                            // Popouts live in their own windows; going home in the main
                            // window must not close them.
                            backStack.removeAll { it !is EmptyPluginNavKey && it !is PluginPopoutNavKey }
                        },
                        onNavigateSettings = { page ->
                            backStack.addSingleTop(SettingsNavKey(initialPage = page))
                        },
                        onNavigateLogViewer = { backStack.addSingleTop(LogViewerNavKey) },
                        onSelectedSessionChange = { selectedSession ->
                            // When the user switches the active session, make any plugin screen
                            // currently on top follow the newly-selected session instead of
                            // lingering on the previous one.
                            backStack.followPluginToSession(
                                newSessionId = selectedSession.id,
                                isPluginAvailableOnNewSession = { pluginId ->
                                    selectedSession.installedPlugins.any { it.pluginId == pluginId }
                                },
                            )
                        },
                    ) {
                        HostWindowContent(
                            backStack = backStack,
                            availableUpdate = availableUpdate,
                            isUpdateBannerDismissed = isUpdateBannerDismissed,
                            onDismissUpdateBanner = onDismissUpdateBanner,
                            onClickOpenUpdateSettings = {
                                onDismissUpdateBanner()
                                backStack.addSingleTop(SettingsNavKey())
                            },
                            onClickReviewArrivedPlugins = {
                                backStack.addSingleTop(SettingsNavKey(initialPage = SettingsScreenPage.PluginSecurity))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
context(appGraph: JetWhaleAppGraph)
private fun HostWindowContent(
    backStack: NavBackStack<NavKey>,
    availableUpdate: UpdateCheckResult?,
    isUpdateBannerDismissed: Boolean,
    onDismissUpdateBanner: () -> Unit,
    onClickOpenUpdateSettings: () -> Unit,
    onClickReviewArrivedPlugins: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val arrivedJars = rememberSubscription(appGraph.arrivedPluginJarsSubscriptionKey).data?.jars ?: persistentListOf()
    val trustPluginMutation = rememberMutation(appGraph.trustPluginMutationKey)
    val postponeMutation = rememberMutation(appGraph.postponeArrivedPluginJarMutationKey)
    val coroutineScope = rememberCoroutineScope()
    Column(modifier = modifier) {
        AnimatedVisibility(
            visible = availableUpdate != null && !isUpdateBannerDismissed,
            enter = slideInVertically(initialOffsetY = Int::unaryMinus) + expandVertically(expandFrom = Alignment.Top),
            exit = slideOutVertically(targetOffsetY = Int::unaryMinus) + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            // Non-null while visible; stays rendered during the exit animation because dismissing
            // only flips the flag.
            availableUpdate?.let { update ->
                UpdateAvailableBanner(
                    latestVersion = update.latestVersion,
                    onClickOpenSettings = onClickOpenUpdateSettings,
                    onDismiss = onDismissUpdateBanner,
                )
            }
        }
        AnimatedVisibility(
            visible = arrivedJars.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = Int::unaryMinus) + expandVertically(expandFrom = Alignment.Top),
            exit = slideOutVertically(targetOffsetY = Int::unaryMinus) + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            PluginJarArrivalBanner(
                arrivedJars = arrivedJars,
                // A jar that cannot be loaded ends up among the failed jars in the plugin settings.
                // Approves the content the banner showed, not whatever is at the path by now.
                onLoad = { jar, replaceOtherVersions ->
                    coroutineScope.launch { runCatching { trustPluginMutation.mutateAsync(TrustPluginRequest(jar.jarPath, jar.sha256, replaceOtherVersions)) } }
                },
                onPostpone = { jarPath -> coroutineScope.launch { postponeMutation.mutateAsync(PostponeArrivedPluginJarRequest(jarPath)) } },
                onReviewInSettings = onClickReviewArrivedPlugins,
            )
        }
        JetWhaleNavDisplay(backStack)
    }
}
