package com.kitakkun.jetwhale.debugger.host.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import com.kitakkun.jetwhale.debugger.host.di.JetWhaleAppGraph
import com.kitakkun.jetwhale.debugger.host.plugin.PluginScreenRoot
import com.kitakkun.jetwhale.debugger.host.screen.EmptyPluginScreen
import com.kitakkun.jetwhale.debugger.host.screen.InfoScreen
import com.kitakkun.jetwhale.debugger.host.settings.SettingsScreenRoot
import io.github.takahirom.rin.rememberRetained

fun EntryProviderScope<NavKey>.infoEntry() {
    entry<InfoNavKey>(
        metadata = DialogSceneStrategy.dialog(),
    ) {
        InfoScreen()
    }
}

fun EntryProviderScope<NavKey>.emptyPluginEntry() {
    entry<EmptyPluginNavKey> {
        EmptyPluginScreen()
    }
}

context(appGraph: JetWhaleAppGraph)
fun EntryProviderScope<NavKey>.pluginEntry() {
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