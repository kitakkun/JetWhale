package com.kitakkun.jetwhale.plugins.permissions.agent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.plugins.permissions.protocol.GetPermissions
import com.kitakkun.jetwhale.plugins.permissions.protocol.OpenAppSettings
import com.kitakkun.jetwhale.plugins.permissions.protocol.PERMISSIONS_PLUGIN_ID
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionReport
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionsChanged
import com.kitakkun.jetwhale.plugins.permissions.protocol.RequestPermission
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.reply
import com.kitakkun.jetwhale.protocol.messaging.trySend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * How often the agent re-reads the permissions while a host is connected, to notice a change.
 * Nothing notifies an app when the user flips a permission in the system settings, so the agent
 * looks; one read is a handful of cheap platform calls.
 */
private val PollInterval = 1.seconds

/**
 * Agent plugin that reports the app's permissions to the host, requests them on the host's
 * behalf, and tells the host whenever one changes. It needs no configuration:
 *
 * ```kotlin
 * startJetWhale { plugins { register(JetWhalePermissionsAgentPlugin()) } }
 * ```
 *
 * On Android it reports every permission the app declares plus the special accesses it can query;
 * on iOS the privacy permissions of the frameworks listed in the guide. Desktop and the web report
 * themselves as unsupported.
 */
class JetWhalePermissionsAgentPlugin : JetWhaleAgentPlugin() {
    override val pluginId: String get() = PERMISSIONS_PLUGIN_ID
    override val pluginVersion: String get() = "1.0.0"

    private val source: PermissionSource = platformPermissionSource()
    private val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var watch: Job? = null

    override fun JetWhaleMessageHandlers.configure() {
        onRequest { _: GetPermissions -> reply(report()) }
        onRequest { request: RequestPermission -> reply(source.request(request.id)) }
        onRequest { _: OpenAppSettings -> reply(source.openAppSettings()) }
    }

    override suspend fun onPrepare() {
        watch?.cancel()
        watch = watchScope.launch {
            var previous = source.read()
            while (isActive) {
                delay(PollInterval)
                val current = source.read()
                val changes = diffPermissions(previous, current, Clock.System.now().toEpochMilliseconds())
                if (changes.isNotEmpty()) messenger.trySend(PermissionsChanged(changes))
                previous = current
            }
        }
    }

    override suspend fun onDisconnected() {
        watch?.cancel()
    }

    override fun onDeactivate() {
        watch?.cancel()
    }

    private suspend fun report(): PermissionReport = PermissionReport(
        platform = source.platform,
        unsupportedReason = source.unsupportedReason,
        permissions = source.read(),
    )
}
