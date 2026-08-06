package com.kitakkun.jetwhale.host.settings.licenses

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
data object LicensesNavKey : NavKey

/** Everything the OSS licenses screen can ask the host to navigate to. */
interface LicensesNavigator {
    fun back()
}

@Inject
@ContributesIntoSet(AppScope::class)
class LicensesNavEntryProvider(
    private val screenContext: LicensesScreenContext,
    private val navigator: LicensesNavigator,
) : NavEntryProvider {
    context(scope: EntryProviderScope<NavKey>)
    override fun provideEntry() {
        scope.entry<LicensesNavKey>(
            metadata = StableDialogSceneStrategy.dialog(
                dialogProperties = DialogProperties(
                    usePlatformDefaultWidth = false,
                ),
            ),
        ) {
            context(screenContext) {
                LicensesScreenRoot(
                    onClickBack = navigator::back,
                )
            }
        }
    }
}
