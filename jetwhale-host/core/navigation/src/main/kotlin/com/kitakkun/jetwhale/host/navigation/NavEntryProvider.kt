package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * Contributes exactly one [androidx.navigation3.runtime.NavEntry] to the host's entry provider.
 *
 * One implementation provides one entry, and it lives in the module that owns the screen it shows.
 * The navigation host collects every implementation from the dependency graph, so adding a screen
 * means adding a class rather than editing a central list.
 */
interface NavEntryProvider {
    context(scope: EntryProviderScope<NavKey>)
    fun provideEntry()
}
