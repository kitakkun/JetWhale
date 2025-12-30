package com.kitakkun.jetwhale.debugger.host.data.settings

import com.kitakkun.jetwhale.debugger.host.model.AppAppearanceRepository
import com.kitakkun.jetwhale.debugger.host.model.JetWhaleTheme
import com.kitakkun.jetwhale.debugger.host.model.ThemeSubscriptionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.map
import soil.query.SubscriptionId
import soil.query.SubscriptionInitialData
import soil.query.buildSubscriptionKey

@ContributesBinding(AppScope::class)
@Inject
class DefaultThemeSubscriptionKey(
    private val appAppearanceRepository: AppAppearanceRepository,
) : ThemeSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("theme_subscription_key"),
    subscribe = {
        appAppearanceRepository.preferredColorSchemeFlow.map {
            JetWhaleTheme(colorScheme = it)
        }
    }
) {
    override fun onInitialData(): SubscriptionInitialData<JetWhaleTheme> {
        return { JetWhaleTheme() }
    }
}
