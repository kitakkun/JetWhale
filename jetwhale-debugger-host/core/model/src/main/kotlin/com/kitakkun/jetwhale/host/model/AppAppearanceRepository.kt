package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.Flow

interface AppAppearanceRepository {
    val languageFlow: Flow<AppLanguage>
    val preferredColorSchemeIdFlow: Flow<JetWhaleColorSchemeId>
    suspend fun setPreferredColorSchemeId(id: JetWhaleColorSchemeId)
    suspend fun saveCustomTheme(id: JetWhaleColorSchemeId, theme: JetWhaleColorScheme.Static.Custom)
    suspend fun saveDynamicTheme(id: JetWhaleColorSchemeId, lightThemeId: JetWhaleColorSchemeId, darkThemeId: JetWhaleColorSchemeId)
    suspend fun updateAppLanguage(language: AppLanguage)
    val preferredColorSchemeFlow: Flow<JetWhaleColorScheme>
}
