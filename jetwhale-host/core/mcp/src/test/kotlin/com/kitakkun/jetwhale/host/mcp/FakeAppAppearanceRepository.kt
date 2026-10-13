package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.AppAppearanceRepository
import com.kitakkun.jetwhale.host.model.AppLanguage
import com.kitakkun.jetwhale.host.model.JetWhaleColorScheme
import com.kitakkun.jetwhale.host.model.JetWhaleColorSchemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/** In-memory appearance settings, starting from the defaults a fresh install has. */
class FakeAppAppearanceRepository : AppAppearanceRepository {
    override val preferredColorSchemeIdFlow: StateFlow<JetWhaleColorSchemeId>
        field = MutableStateFlow(JetWhaleColorSchemeId.BuiltInDynamic)

    override val languageFlow: Flow<AppLanguage> = flowOf(AppLanguage.English)

    override val preferredColorSchemeFlow: Flow<JetWhaleColorScheme> = emptyFlow()

    override suspend fun setPreferredColorSchemeId(id: JetWhaleColorSchemeId) {
        preferredColorSchemeIdFlow.value = id
    }

    override suspend fun updateAppLanguage(language: AppLanguage) = Unit
}
