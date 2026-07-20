package com.kitakkun.jetwhale.host.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.LocalComposeWindow
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.di.JetWhaleAppGraph
import com.kitakkun.jetwhale.host.log_viewer_window_title
import com.kitakkun.jetwhale.host.plugin.PluginScreenRoot
import com.kitakkun.jetwhale.host.screen.EmptyPluginScreen
import com.kitakkun.jetwhale.host.screen.InfoScreen
import com.kitakkun.jetwhale.host.settings.SettingsScreenRoot
import com.kitakkun.jetwhale.host.settings.licenses.LicensesScreenRoot
import com.kitakkun.jetwhale.host.settings.logviewer.LogViewerScreenRoot
import org.jetbrains.compose.resources.stringResource

fun EntryProviderScope<NavKey>.infoEntry(
    onClickOSSLicenses: () -> Unit,
) {
    entry<InfoNavKey>(
        metadata = StableDialogSceneStrategy.dialog(),
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
        context(
            retain {
                appGraph.pluginScreenContextFactory.create(
                    pluginId = navKey.pluginId,
                    sessionId = navKey.sessionId,
                )
            },
        ) {
            if (isOpenedOnPopout(navKey.pluginId, navKey.sessionId)) {
                PluginPoppedOutScreen(
                    onBringbackToMainWindow = {
                        onBringbackToMainWindow(navKey.pluginId, navKey.sessionId)
                    },
                )
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
            ),
        ),
    ) { navKey ->
        val window = LocalComposeWindow.current

        LaunchedEffect(window, navKey) {
            window.title = "${navKey.pluginName} on ${navKey.sessionId}"
        }

        context(
            retain {
                appGraph.pluginScreenContextFactory.create(
                    pluginId = navKey.pluginId,
                    sessionId = navKey.sessionId,
                )
            },
        ) {
            Surface {
                PluginScreenRoot()
            }
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
    onClickClose: () -> Unit,
    onOpenLogViewer: () -> Unit,
) {
    entry<SettingsNavKey>(
        metadata = StableDialogSceneStrategy.dialog(
            dialogProperties = DialogProperties(
                usePlatformDefaultWidth = false,
            ),
        ),
    ) { navKey ->
        context(
            retain {
                appGraph.settingsScreenContext
            },
        ) {
            SettingsScreenRoot(
                initialMenu = navKey.initialMenu,
                onClickClose = onClickClose,
                onOpenLogViewer = onOpenLogViewer,
            )
        }
    }
}

context(appGraph: JetWhaleAppGraph)
fun EntryProviderScope<NavKey>.licensesEntry(
    onClickBack: () -> Unit,
) {
    entry<LicensesNavKey>(
        metadata = StableDialogSceneStrategy.dialog(
            dialogProperties = DialogProperties(
                usePlatformDefaultWidth = false,
            ),
        ),
    ) {
        context(
            retain {
                appGraph.licensesScreenContext
            },
        ) {
            LicensesScreenRoot(
                onClickBack = onClickBack,
            )
        }
    }
}

context(appGraph: JetWhaleAppGraph)
fun EntryProviderScope<NavKey>.logViewerEntry() {
    entry<LogViewerNavKey>(
        metadata = WindowSceneStrategy.window(
            WindowProperties(
                width = 1000.dp,
                height = 700.dp,
            ),
        ),
    ) {
        val window = LocalComposeWindow.current
        val windowTitle = stringResource(Res.string.log_viewer_window_title)

        LaunchedEffect(window) {
            window.title = windowTitle
        }

        context(
            retain {
                appGraph.settingsScreenContext
            },
        ) {
            LogViewerScreenRoot()
        }
    }
}
