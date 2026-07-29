package com.kitakkun.jetwhale.host.settings.logviewer

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.navigation.LocalComposeWindow
import com.kitakkun.jetwhale.host.navigation.NavEntryProvider
import com.kitakkun.jetwhale.host.navigation.WindowProperties
import com.kitakkun.jetwhale.host.navigation.WindowSceneStrategy
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.SettingsScreenContext
import com.kitakkun.jetwhale.host.settings.log_viewer_window_title
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

@Serializable
data object LogViewerNavKey : NavKey

@Inject
@ContributesIntoSet(AppScope::class)
class LogViewerNavEntryProvider(
    private val screenContext: SettingsScreenContext,
) : NavEntryProvider {
    context(scope: EntryProviderScope<NavKey>)
    override fun provideEntry() {
        scope.entry<LogViewerNavKey>(
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

            context(screenContext) {
                LogViewerScreenRoot()
            }
        }
    }
}
