package com.kitakkun.jetwhale.host.settings

import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.navigation.NavEntryProvider
import com.kitakkun.jetwhale.host.navigation.StableDialogSceneStrategy
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.serialization.Serializable

@Serializable
data class SettingsNavKey(
    val initialPage: SettingsScreenPage = SettingsScreenPage.Appearance,
) : NavKey

/** Everything the settings screen can ask the host to navigate to. */
interface SettingsNavigator {
    fun close()

    fun openLogViewer()
}

@Inject
@ContributesIntoSet(AppScope::class)
class SettingsNavEntryProvider(
    private val screenContext: SettingsScreenContext,
    private val navigator: SettingsNavigator,
) : NavEntryProvider {
    context(scope: EntryProviderScope<NavKey>)
    override fun provideEntry() {
        scope.entry<SettingsNavKey>(
            metadata = StableDialogSceneStrategy.dialog(
                dialogProperties = DialogProperties(
                    usePlatformDefaultWidth = false,
                ),
            ),
        ) { navKey ->
            context(screenContext) {
                SettingsScreenRoot(
                    initialPage = navKey.initialPage,
                    onClickClose = navigator::close,
                    onOpenLogViewer = navigator::openLogViewer,
                )
            }
        }
    }
}
