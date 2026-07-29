package com.kitakkun.jetwhale.host.screen

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.navigation.NavEntryProvider
import com.kitakkun.jetwhale.host.shell.EmptyPluginNavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

@Inject
@ContributesIntoSet(AppScope::class)
class EmptyPluginNavEntryProvider : NavEntryProvider {
    context(scope: EntryProviderScope<NavKey>)
    override fun provideEntry() {
        scope.entry<EmptyPluginNavKey> {
            EmptyPluginScreen()
        }
    }
}
