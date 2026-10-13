package com.kitakkun.jetwhale.host.plugin

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.architecture.ExplicitScreenContextUsage
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.withScreenContext
import com.kitakkun.jetwhale.host.model.AppearanceSettingsSubscriptionKey
import com.kitakkun.jetwhale.host.model.DynamicPluginBridgeProvider
import com.kitakkun.jetwhale.host.model.ThemeSubscriptionKey
import com.kitakkun.jetwhale.host.theme.AppEnvironment
import com.kitakkun.jetwhale.host.theme.HostTheme
import com.kitakkun.jetwhale.host.theme.clearFocusOnBlankPress
import com.kitakkun.jetwhale.host.ui.JwSurface
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
                    HostTheme(theme.colorScheme) {
                        JwSurface(modifier = Modifier.fillMaxSize().clearFocusOnBlankPress()) {
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
