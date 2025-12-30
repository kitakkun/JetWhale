package com.kitakkun.jetwhale.debugger.host.plugin

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.debugger.host.architecture.ExplicitScreenContextUsage
import com.kitakkun.jetwhale.debugger.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.debugger.host.architecture.withScreenContext
import com.kitakkun.jetwhale.debugger.host.model.AppearanceSettingsSubscriptionKey
import com.kitakkun.jetwhale.debugger.host.model.DynamicPluginBridgeProvider
import com.kitakkun.jetwhale.debugger.host.model.ThemeSubscriptionKey
import com.kitakkun.jetwhale.debugger.host.ui.AppEnvironment
import com.kitakkun.jetwhale.debugger.host.ui.JetWhaleTheme
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
                        AppEnvironment(appearanceSettings.appLanguage) {
                            content()
                        }
                    }
                }
            }
        }
    }
}
