package com.kitakkun.jetwhale.host.data.settings

import com.kitakkun.jetwhale.host.model.DebuggerBehaviorSettings
import com.kitakkun.jetwhale.host.model.SettingsSubscriptionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
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
            defaultDebuggerSettingsRepository.checkForUpdatesOnStartupFlow,
            defaultDebuggerSettingsRepository.persistDataFlow,
            defaultDebuggerSettingsRepository.serverPortFlow,
            defaultDebuggerSettingsRepository.mcpServerPortFlow,
            defaultDebuggerSettingsRepository.wssPortFlow,
            defaultDebuggerSettingsRepository.wssEnabledFlow,
        ) { adbAutoPortMappingEnabled, checkForUpdatesOnStartup, persistData, serverPort, mcpServerPort, wssPort, wssEnabled ->
            DebuggerBehaviorSettings(
                adbAutoPortMappingEnabled = adbAutoPortMappingEnabled,
                checkForUpdatesOnStartup = checkForUpdatesOnStartup,
                persistData = persistData,
                serverPort = serverPort,
                mcpServerPort = mcpServerPort,
                wssPort = wssPort,
                wssEnabled = wssEnabled,
            )
        }
    },
)

/** Seven-flow overload of [combine], which kotlinx.coroutines only provides up to five flows. */
@Suppress("UNCHECKED_CAST")
private inline fun <reified T1, reified T2, reified T3, reified T4, reified T5, reified T6, reified T7, R> combine(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7) -> R,
): Flow<R> = combine(flow1, flow2, flow3, flow4, flow5, flow6, flow7) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7,
    )
}
