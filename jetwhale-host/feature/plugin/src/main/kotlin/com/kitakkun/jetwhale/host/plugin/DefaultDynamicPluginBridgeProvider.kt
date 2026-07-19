package com.kitakkun.jetwhale.host.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.kitakkun.jetwhale.host.architecture.ExplicitScreenContextUsage
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.withScreenContext
import com.kitakkun.jetwhale.host.model.AppearanceSettingsSubscriptionKey
import com.kitakkun.jetwhale.host.model.DynamicPluginBridgeProvider
import com.kitakkun.jetwhale.host.model.ThemeSubscriptionKey
import com.kitakkun.jetwhale.host.sdk.LocalJetWhaleDarkTheme
import com.kitakkun.jetwhale.host.ui.AppEnvironment
import com.kitakkun.jetwhale.host.ui.JetWhaleTheme
import com.kitakkun.jetwhale.host.ui.isDarkTheme
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.SwrClientPlus
import soil.query.compose.SwrClientProvider
import soil.query.compose.rememberSubscription

@ContributesBinding(AppScope::class)
@Inject
class DefaultDynamicPluginBridgeProvider(
    private val themeSubscriptionKey: ThemeSubscriptionKey,
    private val appearanceSettingsSubscriptionKey: AppearanceSettingsSubscriptionKey,
    private val swrClient: SwrClientPlus,
) : DynamicPluginBridgeProvider {
    @OptIn(ExplicitScreenContextUsage::class)
    @Composable
    override fun PluginEntryPoint(content: @Composable () -> Unit) {
        withScreenContext {
            SwrClientProvider(swrClient) {
                SoilDataBoundary(
                    state1 = rememberSubscription(themeSubscriptionKey),
                    state2 = rememberSubscription(appearanceSettingsSubscriptionKey),
                ) { theme, appearanceSettings ->
                    JetWhaleTheme(theme.colorScheme) {
                        CompositionLocalProvider(
                            // Decided from the scheme itself (definitive for Light/Dark, OS for
                            // Dynamic), not a luminance guess of the resolved surface.
                            LocalJetWhaleDarkTheme provides theme.colorScheme.isDarkTheme(),
                        ) {
                            AppEnvironment(appearanceSettings.appLanguage) {
                                content()
                            }
                        }
                    }
                }
            }
        }
    }
}
