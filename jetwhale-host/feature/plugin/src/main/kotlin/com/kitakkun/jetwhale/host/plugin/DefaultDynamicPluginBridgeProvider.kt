package com.kitakkun.jetwhale.host.plugin

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.ExplicitScreenContextUsage
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.withScreenContext
import com.kitakkun.jetwhale.host.model.AppearanceSettingsSubscriptionKey
import com.kitakkun.jetwhale.host.model.DynamicPluginBridgeProvider
import com.kitakkun.jetwhale.host.model.JetWhaleDebugOperationContextQueryKey
import com.kitakkun.jetwhale.host.model.ThemeSubscriptionKey
import com.kitakkun.jetwhale.host.sdk.JetWhaleDebugOperationContext
import com.kitakkun.jetwhale.host.ui.AppEnvironment
import com.kitakkun.jetwhale.host.ui.JetWhaleTheme
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.SwrClientPlus
import soil.query.compose.SwrClientProvider
import soil.query.compose.rememberQuery
import soil.query.compose.rememberSubscription

@ContributesBinding(AppScope::class)
@Inject
class DefaultDynamicPluginBridgeProvider(
    private val themeSubscriptionKey: ThemeSubscriptionKey,
    private val appearanceSettingsSubscriptionKey: AppearanceSettingsSubscriptionKey,
    private val debugOperationContextQueryKey: JetWhaleDebugOperationContextQueryKey,
    private val swrClient: SwrClientPlus,
) : DynamicPluginBridgeProvider {
    @OptIn(ExplicitScreenContextUsage::class)
    @Composable
    override fun PluginEntryPoint(
        content: @Composable (context: JetWhaleDebugOperationContext<String, String>) -> Unit,
    ) {
        withScreenContext {
            SwrClientProvider(swrClient) {
                SoilDataBoundary(
                    state1 = rememberSubscription(themeSubscriptionKey),
                    state2 = rememberSubscription(appearanceSettingsSubscriptionKey),
                    state3 = rememberQuery(debugOperationContextQueryKey),
                ) { theme, appearanceSettings, debugOperationContext ->
                    JetWhaleTheme(theme.colorScheme) {
                        AppEnvironment(appearanceSettings.appLanguage) {
                            content(debugOperationContext)
                        }
                    }
                }
            }
        }
    }
}
