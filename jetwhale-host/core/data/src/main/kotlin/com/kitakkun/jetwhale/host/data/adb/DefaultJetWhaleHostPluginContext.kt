package com.kitakkun.jetwhale.host.data.adb

import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginContext
import com.kitakkun.jetwhale.host.sdk.JetWhaleSessionInfo
import com.kitakkun.jetwhale.host.sdk.JetWhaleSessions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The host services handed to every plugin instance. One object serves them all: it exposes only
 * host-wide capabilities, so nothing in it is scoped to a single plugin or session.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultJetWhaleHostPluginContext(
    override val adb: JetWhaleAdb,
    debugSessionRepository: DebugSessionRepository,
) : JetWhaleHostPluginContext {
    override val sessions: JetWhaleSessions = DefaultJetWhaleSessions(debugSessionRepository)
}

private class DefaultJetWhaleSessions(debugSessionRepository: DebugSessionRepository) : JetWhaleSessions {
    // Lives as long as the host itself; the flow it shares is read by plugin instances that come and
    // go, so it is kept hot rather than restarted per subscriber.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override val active: StateFlow<List<JetWhaleSessionInfo>> = debugSessionRepository.debugSessionsFlow
        .map { sessions ->
            sessions.filter { it.isActive }.map { session ->
                JetWhaleSessionInfo(
                    sessionId = session.id,
                    appName = session.appName,
                    deviceId = session.deviceId,
                    deviceName = session.deviceName,
                    installedPluginIds = session.installedPlugins.map { it.pluginId },
                )
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
}
