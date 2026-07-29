package com.kitakkun.jetwhale.host.plugin

import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.navigation.LocalComposeWindow
import com.kitakkun.jetwhale.host.navigation.NavEntryProvider
import com.kitakkun.jetwhale.host.navigation.WindowProperties
import com.kitakkun.jetwhale.host.navigation.WindowSceneStrategy
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

@Inject
@ContributesIntoSet(AppScope::class)
class PluginPopoutNavEntryProvider(
    private val screenContextFactory: PluginScreenContext.Factory,
) : NavEntryProvider {
    context(scope: EntryProviderScope<NavKey>)
    override fun provideEntry() {
        scope.entry<PluginPopoutNavKey>(
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
                    screenContextFactory.create(
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
}
