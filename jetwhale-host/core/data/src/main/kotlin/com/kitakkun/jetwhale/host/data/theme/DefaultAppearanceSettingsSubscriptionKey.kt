package com.kitakkun.jetwhale.host.data.theme

import com.kitakkun.jetwhale.host.model.AppAppearanceRepository
import com.kitakkun.jetwhale.host.model.AppearanceSettings
import com.kitakkun.jetwhale.host.model.AppearanceSettingsSubscriptionKey
import com.kitakkun.jetwhale.host.model.JetWhaleColorSchemeId
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.combine
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@ContributesBinding(AppScope::class)
@Inject
class DefaultAppearanceSettingsSubscriptionKey(
    private val appAppearanceRepository: AppAppearanceRepository,
) : AppearanceSettingsSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("appearance_settings"),
    subscribe = {
        combine(
            appAppearanceRepository.languageFlow,
            appAppearanceRepository.preferredColorSchemeIdFlow,
        ) { language, preferredColorSchemeId ->
            AppearanceSettings(
                appLanguage = language,
                activeColorScheme = preferredColorSchemeId,
                availableColorSchemes = persistentListOf(*JetWhaleColorSchemeId.BuiltIns.toTypedArray())
            )
        }
    }
)
