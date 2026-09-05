package com.kitakkun.jetwhale.host.plugin

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
                    // HostTheme publishes LocalJetWhaleDarkTheme for the plugin, decided from the
                    // scheme itself (definitive for Light/Dark, OS for Dynamic) rather than a
                    // luminance guess of the resolved surface.
                    HostTheme(theme.colorScheme) {
                        // The scene has to carry its own background. On screen the host window
                        // paints one behind it, but an off-screen MCP capture has nothing behind
                        // it, so a plugin that draws no background of its own would be captured
                        // transparent and read as white.
                        Surface(modifier = Modifier.fillMaxSize()) {
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
