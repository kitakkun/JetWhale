package com.kitakkun.jetwhale.host.screen

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.navigation.NavEntryProvider
import com.kitakkun.jetwhale.host.navigation.StableDialogSceneStrategy
import com.kitakkun.jetwhale.host.shell.InfoNavKey
import com.kitakkun.jetwhale.host.shell.InfoNavigator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

@Inject
@ContributesIntoSet(AppScope::class)
class InfoNavEntryProvider(
    private val navigator: InfoNavigator,
) : NavEntryProvider {
    context(scope: EntryProviderScope<NavKey>)
    override fun provideEntry() {
        scope.entry<InfoNavKey>(
            metadata = StableDialogSceneStrategy.dialog(),
        ) {
            InfoScreen(
                onClickOSSLicenses = navigator::openLicenses,
            )
        }
    }
}
