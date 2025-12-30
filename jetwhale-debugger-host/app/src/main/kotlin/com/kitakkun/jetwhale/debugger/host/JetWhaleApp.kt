package com.kitakkun.jetwhale.debugger.host

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import com.kitakkun.jetwhale.debugger.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.debugger.host.architecture.SoilFallbackDefaults
import com.kitakkun.jetwhale.debugger.host.di.JetWhaleAppGraph
import com.kitakkun.jetwhale.debugger.host.drawer.ToolingScaffoldRoot
import com.kitakkun.jetwhale.debugger.host.navigation.EmptyPluginNavKey
import com.kitakkun.jetwhale.debugger.host.navigation.InfoNavKey
import com.kitakkun.jetwhale.debugger.host.navigation.JetWhaleNavDisplay
import com.kitakkun.jetwhale.debugger.host.navigation.PluginNavKey
import com.kitakkun.jetwhale.debugger.host.navigation.SettingsNavKey
import com.kitakkun.jetwhale.debugger.host.navigation.addSingleTop
import com.kitakkun.jetwhale.debugger.host.ui.AppEnvironment
import com.kitakkun.jetwhale.debugger.host.ui.JetWhaleTheme
import io.github.takahirom.rin.rememberRetained
import kotlinx.serialization.modules.SerializersModule
import soil.query.compose.SwrClientProvider
import soil.query.compose.rememberSubscription

context(appGraph: JetWhaleAppGraph)
@Composable
fun JetWhaleApp() {
    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class, EmptyPluginNavKey::class, EmptyPluginNavKey.serializer())
                polymorphic(NavKey::class, SettingsNavKey::class, SettingsNavKey.serializer())
                polymorphic(NavKey::class, InfoNavKey::class, InfoNavKey.serializer())
                polymorphic(NavKey::class, PluginNavKey::class, PluginNavKey.serializer())
            }
        },
        EmptyPluginNavKey
    )

    LaunchedEffect(backStack) {
        appGraph.debugWebSocketServer.sessionClosedFlow.collect {
            // automatically remove closed plugin sessions from back stack
            backStack.removeAll { navKey ->
                navKey is PluginNavKey && navKey.sessionId == it
            }
            // dispose compose scenes when plugin sessions are closed
            // this cannot be done in the debugWebSocketServer directly because of circular dependencies
            appGraph.pluginComposeSceneRepository.disposePluginSceneForSession(it)
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
