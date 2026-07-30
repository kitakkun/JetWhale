package com.kitakkun.jetwhale.host.plugin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.retain.retain
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.navigation.NavEntryProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

@Inject
@ContributesIntoSet(AppScope::class)
class PluginNavEntryProvider(
    private val screenContextFactory: PluginScreenContext.Factory,
    private val navigator: PluginNavigator,
) : NavEntryProvider {
    context(scope: EntryProviderScope<NavKey>)
    override fun provideEntry() {
        scope.entry<PluginNavKey> { navKey ->
            val poppedOutPlugins by navigator.poppedOutPlugins.collectAsStateWithLifecycle()

            context(
                retain {
                    screenContextFactory.create(
                        pluginId = navKey.pluginId,
                        sessionId = navKey.sessionId,
                    )
                },
            ) {
                if (poppedOutPlugins.isPoppedOut(navKey.pluginId, navKey.sessionId)) {
                    PluginPoppedOutScreen(
                        onBringBackToMainWindow = {
                            navigator.bringBackToMainWindow(navKey.pluginId, navKey.sessionId)
                        },
                    )
                } else {
                    PluginScreenRoot()
                }
            }
        }
    }
}
