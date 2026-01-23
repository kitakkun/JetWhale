package com.kitakkun.jetwhale.host.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import com.kitakkun.jetwhale.host.di.JetWhaleAppGraph
import com.kitakkun.jetwhale.host.plugin.PluginScreenRoot
import com.kitakkun.jetwhale.host.screen.EmptyPluginScreen
import com.kitakkun.jetwhale.host.screen.InfoScreen
import com.kitakkun.jetwhale.host.settings.SettingsScreenRoot
import com.kitakkun.jetwhale.host.settings.licenses.LicensesScreenRoot
import io.github.takahirom.rin.rememberRetained

fun EntryProviderScope<NavKey>.infoEntry(
    onClickOSSLicenses: () -> Unit,
) {
    entry<InfoNavKey>(
        metadata = DialogSceneStrategy.dialog(),
    ) {
        InfoScreen(
            onClickOSSLicenses = onClickOSSLicenses,
        )
    }
}

fun EntryProviderScope<NavKey>.emptyPluginEntry() {
    entry<EmptyPluginNavKey> {
        EmptyPluginScreen()
    }
}

context(appGraph: JetWhaleAppGraph)
fun EntryProviderScope<NavKey>.pluginEntries(
    isOpenedOnPopout: (pluginId: String, sessionId: String) -> Boolean,
    onBringbackToMainWindow: (pluginId: String, sessionId: String) -> Unit,
) {
    entry<PluginNavKey> { navKey ->
        val density = LocalDensity.current
        context(
            rememberRetained {
                appGraph.createPluginScreenContext(
                    pluginId = navKey.pluginId,
                    sessionId = navKey.sessionId,
                    density = density,
                )
            }
        ) {
            if (isOpenedOnPopout(navKey.pluginId, navKey.sessionId)) {
                Surface {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("This plugin is popped out. Please check the separate window.")
                        Button(
                            onClick = {
                                onBringbackToMainWindow(navKey.pluginId, navKey.sessionId)
                            }
                        ) {
                            Text("Bring back to main window")
                        }
                    }
                }
            } else {
                PluginScreenRoot()
            }
        }
    }
    entry<PluginPopoutNavKey>(
        metadata = WindowSceneStrategy.window(
            WindowProperties(
                width = 800.dp,
                height = 600.dp,
            )
        )
    ) { navKey ->
        val density = LocalDensity.current
        context(
            rememberRetained {
                appGraph.createPluginScreenContext(
                    pluginId = navKey.pluginId,
                    sessionId = navKey.sessionId,
                    density = density,
                )
            }
        ) {
            PluginScreenRoot()
        }
    }
}

fun EntryProviderScope<NavKey>.disabledPluginEntry() {
    entry<DisabledPluginNavKey> {
        Surface {
            Box(
                Modifier.fillMaxSize(),
                Alignment.Center,
            ) {
                Text("This plugin is disabled.")
            }
        }
    }
}

context(appGraph: JetWhaleAppGraph)
fun EntryProviderScope<NavKey>.settingsEntry(
    onClickClose: () -> Unit
) {
    entry<SettingsNavKey>(
        metadata = DialogSceneStrategy.dialog(
            dialogProperties = DialogProperties(
                usePlatformDefaultWidth = false,
            )
        ),
    ) {
        context(
            rememberRetained {
                appGraph.createSettingsScreenContext()
            }
        ) {
            SettingsScreenRoot(
                onClickClose = onClickClose,
            )
        }
    }
}

context(appGraph: JetWhaleAppGraph)
fun EntryProviderScope<NavKey>.licensesEntry(
    onClickBack: () -> Unit,
) {
    entry<LicensesNavKey>(
        metadata = DialogSceneStrategy.dialog(
            dialogProperties = DialogProperties(
                usePlatformDefaultWidth = false,
            )
        )
    ) {
        context(
            rememberRetained {
                appGraph.createLicensesScreenContext()
            }
        ) {
            LicensesScreenRoot(
                onClickBack = onClickBack,
            )
        }
    }
}
