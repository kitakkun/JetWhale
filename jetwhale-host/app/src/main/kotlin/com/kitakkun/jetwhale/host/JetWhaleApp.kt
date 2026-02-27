package com.kitakkun.jetwhale.host

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.SoilFallbackDefaults
import com.kitakkun.jetwhale.host.di.JetWhaleAppGraph
import com.kitakkun.jetwhale.host.drawer.ToolingScaffoldRoot
import com.kitakkun.jetwhale.host.navigation.EmptyPluginNavKey
import com.kitakkun.jetwhale.host.navigation.InfoNavKey
import com.kitakkun.jetwhale.host.navigation.JetWhaleNavDisplay
import com.kitakkun.jetwhale.host.navigation.LicensesNavKey
import com.kitakkun.jetwhale.host.navigation.PluginNavKey
import com.kitakkun.jetwhale.host.navigation.PluginPopoutNavKey
import com.kitakkun.jetwhale.host.navigation.SettingsNavKey
import com.kitakkun.jetwhale.host.navigation.addSingleTop
import com.kitakkun.jetwhale.host.ui.AppEnvironment
import com.kitakkun.jetwhale.host.ui.JetWhaleTheme
import io.github.takahirom.rin.rememberRetained
import kotlinx.serialization.modules.SerializersModule
import soil.query.compose.SwrClientProvider
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

    LaunchedEffect(Unit) {
        // dispose plugin scenes when the debug websocket server is stopped, as all plugin sessions will be closed
        appGraph.debugWebSocketServer.serverStoppedFlow.collect {
            backStack.removeAll { navKey ->
                navKey is PluginNavKey || navKey is PluginPopoutNavKey
            }

            appGraph.pluginComposeSceneService.disposeAllPluginScenes()
        }
    }

    LaunchedEffect(backStack) {
        appGraph.debugWebSocketServer.sessionClosedFlow.collect {
            // automatically remove closed plugin sessions from back stack
            backStack.removeAll { navKey ->
                navKey is PluginNavKey && navKey.sessionId == it
            }
            // dispose compose scenes when plugin sessions are closed
            // this cannot be done in the debugWebSocketServer directly because of circular dependencies
            appGraph.pluginComposeSceneService.disposePluginSceneForSession(it)
        }
    }

    LaunchedEffect(backStack) {
        appGraph.enabledPluginsRepository.disabledPluginIdFlow.collect { disabledPluginId ->
            // automatically remove disabled plugin entries from back stack
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

    KeyboardShortcutHandlerProvider(
        onPressSettingsShortcut = { backStack.addSingleTop(SettingsNavKey) },
    ) {
        SwrClientProvider(appGraph.swrClient) {
            SoilDataBoundary(
                state1 = rememberSubscription(appGraph.themeSubscriptionKey),
                state2 = rememberSubscription(appGraph.appearanceSettingsSubscriptionKey),
                fallback = SoilFallbackDefaults.none(),
            ) { theme, settings ->
                JetWhaleTheme(theme.colorScheme) {
                    AppEnvironment(settings.appLanguage) {
                        Surface {
                            context(rememberRetained { appGraph.createToolingScaffoldScreenContext() }) {
                                ToolingScaffoldRoot(
                                    onClickSettings = { backStack.addSingleTop(SettingsNavKey) },
                                    onClickInfo = { backStack.addSingleTop(InfoNavKey) },
                                    onClickPlugin = { pluginId, sessionId ->
                                        backStack.addSingleTop(PluginNavKey(pluginId, sessionId))
                                    },
                                    onClickPopout = { pluginId, pluginName, sessionId ->
                                        backStack.addSingleTop(
                                            PluginPopoutNavKey(
                                                pluginId = pluginId,
                                                sessionId = sessionId,
                                                pluginName = pluginName,
                                            ),
                                        )
                                    },
                                ) {
                                    JetWhaleNavDisplay(backStack)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
