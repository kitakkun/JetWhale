package com.kitakkun.jetwhale.host.drawer

import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.navigation.NavEntryProvider
import com.kitakkun.jetwhale.host.navigation.StableDialogSceneStrategy
import com.kitakkun.jetwhale.host.shell.McpToolsNavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

@Inject
@ContributesIntoSet(AppScope::class)
class McpToolsNavEntryProvider(
    private val screenContext: McpToolsScreenContext,
) : NavEntryProvider {
    context(scope: EntryProviderScope<NavKey>)
    override fun provideEntry() {
        scope.entry<McpToolsNavKey>(
            // The browser sizes itself; the platform default width would squeeze it to a narrow column.
            metadata = StableDialogSceneStrategy.dialog(
                dialogProperties = DialogProperties(
                    usePlatformDefaultWidth = false,
                ),
            ),
        ) { navKey ->
            context(screenContext) {
                McpToolsScreenRoot(
                    initialPluginId = navKey.pluginId,
                    initialSessionId = navKey.sessionId,
                )
            }
        }
    }
}
