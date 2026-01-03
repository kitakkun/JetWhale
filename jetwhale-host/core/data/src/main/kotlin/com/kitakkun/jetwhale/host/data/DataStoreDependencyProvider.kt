package com.kitakkun.jetwhale.host.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@Qualifier
annotation class DebuggerSettingsDataStoreQualifier

@Qualifier
annotation class ThemeDataStoreQualifier

@ContributesTo(AppScope::class)
interface DataStoreDependencyProvider {
    @DebuggerSettingsDataStoreQualifier
    @Provides
    fun provideDebuggerSettingsDataStore(
        appDataDirectoryProvider: AppDataDirectoryProvider,
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.createWithPath(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(Dispatchers.IO),
        ) { appDataDirectoryProvider.resolveDataStoreFilePath("debugger_settings.preferences_pb") }
    }

    @ThemeDataStoreQualifier
    @Provides
    fun provideThemeDataStore(
        appDataDirectoryProvider: AppDataDirectoryProvider,
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.createWithPath(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(Dispatchers.IO),
        ) { appDataDirectoryProvider.resolveDataStoreFilePath("theme.preferences_pb") }
    }
}
