package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.Flow

interface AppAppearanceRepository {
    val languageFlow: Flow<AppLanguage>
    val preferredColorSchemeIdFlow: Flow<JetWhaleColorSchemeId>
    suspend fun setPreferredColorSchemeId(id: JetWhaleColorSchemeId)
    suspend fun updateAppLanguage(language: AppLanguage)
    val preferredColorSchemeFlow: Flow<JetWhaleColorScheme>
}
