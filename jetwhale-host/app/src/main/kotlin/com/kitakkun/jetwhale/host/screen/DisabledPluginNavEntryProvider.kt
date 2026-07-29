package com.kitakkun.jetwhale.host.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.navigation.NavEntryProvider
import com.kitakkun.jetwhale.host.shell.DisabledPluginNavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

@Inject
@ContributesIntoSet(AppScope::class)
class DisabledPluginNavEntryProvider : NavEntryProvider {
    context(scope: EntryProviderScope<NavKey>)
    override fun provideEntry() {
        scope.entry<DisabledPluginNavKey> {
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
}
