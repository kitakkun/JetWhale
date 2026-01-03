package com.kitakkun.jetwhale.host.data.settings

import com.kitakkun.jetwhale.host.model.DebuggerBehaviorSettings
import com.kitakkun.jetwhale.host.model.SettingsSubscriptionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.combine
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultSettingsSubscriptionKey(
    private val defaultDebuggerSettingsRepository: DefaultDebuggerSettingsRepository,
) : SettingsSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("settings"),
    subscribe = {
        combine(
            defaultDebuggerSettingsRepository.adbAutoPortMappingEnabledFlow,
            defaultDebuggerSettingsRepository.persistDataFlow,
        ) { adbAutoPortMappingEnabled, persistData ->
            DebuggerBehaviorSettings(
                adbAutoPortMappingEnabled = adbAutoPortMappingEnabled,
                persistData = persistData,
            )
        }
    }
)
