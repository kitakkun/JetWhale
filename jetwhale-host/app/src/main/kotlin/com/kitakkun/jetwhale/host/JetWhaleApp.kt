package com.kitakkun.jetwhale.host

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.SoilFallbackDefaults
import com.kitakkun.jetwhale.host.component.UpdateAvailableBanner
import com.kitakkun.jetwhale.host.di.JetWhaleAppGraph
import com.kitakkun.jetwhale.host.drawer.ToolingScaffoldRoot
import com.kitakkun.jetwhale.host.plugin.PluginNavKey
import com.kitakkun.jetwhale.host.plugin.isPoppedOut
import com.kitakkun.jetwhale.host.settings.SettingsNavKey
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import com.kitakkun.jetwhale.host.settings.licenses.LicensesNavKey
import com.kitakkun.jetwhale.host.shell.EmptyPluginNavKey
import com.kitakkun.jetwhale.host.shell.InfoNavKey
import com.kitakkun.jetwhale.host.ui.AppEnvironment
import com.kitakkun.jetwhale.host.ui.JetWhaleTheme
import kotlinx.serialization.modules.SerializersModule
import soil.query.compose.SwrClientProvider
import soil.query.compose.rememberMutation
import soil.query.compose.rememberSubscription

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
                polymorphic(NavKey::class, LicensesNavKey::class, LicensesNavKey.serializer())
            }
        },
        EmptyPluginNavKey,
    )

    val navigator = appGraph.toolingScaffoldNavigator
    val pluginNavigator = appGraph.pluginNavigator
    val poppedOutPlugins by pluginNavigator.poppedOutPlugins.collectAsStateWithLifecycle()

    // The only writer of the back stack, and what publishes it back so the navigators — and the MCP
    // server through them — can read what the window shows.
    NavigatorEffect(backStack = backStack, navigationBus = appGraph.navigationBus)

    // Scenes created for a caller that never displays them (the MCP screenshot tool, say) would
    // otherwise lay out at density 1.0 and disagree with what this window shows.
    val density = LocalDensity.current
    LaunchedEffect(density) {
        appGraph.pluginComposeSceneService.updateHostDensity(density)
    }

    LaunchedEffect(Unit) {
        // dispose plugin scenes when the debug websocket server is stopped, as all plugin sessions will be closed
        appGraph.debugWebSocketServer.serverStoppedFlow.collect {
            pluginNavigator.closeAllPluginScreens()

            appGraph.pluginComposeSceneService.disposeAllPluginScenes()
        }
    }

    LaunchedEffect(Unit) {
        appGraph.debugWebSocketServer.sessionClosedFlow.collect {
            // automatically remove closed plugin sessions from back stack
            pluginNavigator.closePluginScreensForSession(it)
            // dispose compose scenes when plugin sessions are closed
            // this cannot be done in the debugWebSocketServer directly because of circular dependencies
            appGraph.pluginComposeSceneService.disposePluginSceneForSession(it)
        }
    }

    LaunchedEffect(Unit) {
        appGraph.enabledPluginsRepository.disabledPluginIdFlow.collect { disabledPluginId ->
            // automatically remove disabled plugin entries from back stack
            pluginNavigator.closePluginScreensForPlugin(disabledPluginId)

            appGraph.pluginComposeSceneService.disposePluginScenesForPlugin(disabledPluginId)
        }
    }

    KeyboardShortcutHandlerProvider(
        onPressSettingsShortcut = navigator::openSettings,
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
            val availableUpdate = updateCheckMutation.data?.takeIf { it.updateAvailable }

            SoilDataBoundary(
                state1 = rememberSubscription(appGraph.themeSubscriptionKey),
                state2 = rememberSubscription(appGraph.appearanceSettingsSubscriptionKey),
                fallback = SoilFallbackDefaults.none(),
            ) { theme, settings ->
                JetWhaleTheme(theme.colorScheme) {
                    AppEnvironment(settings.appLanguage) {
                        Surface {
                            context(retain { appGraph.toolingScaffoldScreenContext }) {
                                ToolingScaffoldRoot(
                                    onClickSettings = navigator::openSettings,
                                    onClickPluginSettings = {
                                        navigator.openSettings(SettingsScreenPage.InstalledPlugins)
                                    },
                                    onClickInfo = navigator::openInfo,
                                    onClickPlugin = pluginNavigator::openPlugin,
                                    onOpenMcpTools = navigator::openMcpTools,
                                    onClickPopout = pluginNavigator::popOut,
                                    isPoppedOut = poppedOutPlugins::isPoppedOut,
                                    onClickBringBack = pluginNavigator::bringBackToMainWindow,
                                    onSelectedSessionChange = { selectedSession ->
                                        // When the user switches the active session, make any plugin screen
                                        // currently on top follow the newly-selected session instead of
                                        // lingering on the previous one.
                                        pluginNavigator.followPluginToSession(
                                            newSessionId = selectedSession.id,
                                            availablePluginIds = selectedSession.installedPlugins
                                                .mapTo(mutableSetOf()) { it.pluginId },
                                        )
                                    },
                                ) {
                                    Column {
                                        AnimatedVisibility(
                                            visible = availableUpdate != null && !updateBannerDismissed,
                                            enter = slideInVertically { -it } + expandVertically(expandFrom = Alignment.Top),
                                            exit = slideOutVertically { -it } + shrinkVertically(shrinkTowards = Alignment.Top),
                                        ) {
                                            // Non-null while visible; stays rendered during the exit
                                            // animation because dismissing only flips the flag.
                                            availableUpdate?.let { update ->
                                                UpdateAvailableBanner(
                                                    latestVersion = update.latestVersion,
                                                    onClickOpenSettings = {
                                                        updateBannerDismissed = true
                                                        navigator.openSettings()
                                                    },
                                                    onDismiss = { updateBannerDismissed = true },
                                                )
                                            }
                                        }
                                        JetWhaleNavDisplay(
                                            backStack = backStack,
                                            entryProviders = appGraph.navEntryProviders,
                                            navigationBus = appGraph.navigationBus,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
