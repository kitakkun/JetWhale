package com.kitakkun.jetwhale.plugins.background.agent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.plugins.background.protocol.BACKGROUND_WORK_PLUGIN_ID
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkChanged
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkSnapshot
import com.kitakkun.jetwhale.plugins.background.protocol.CancelWork
import com.kitakkun.jetwhale.plugins.background.protocol.GetBackgroundWork
import com.kitakkun.jetwhale.plugins.background.protocol.RunWorkNow
import com.kitakkun.jetwhale.plugins.background.protocol.WorkOperationResult
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.reply
import com.kitakkun.jetwhale.protocol.messaging.trySend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Agent plugin that reports the app's scheduled background work to the host, and cancels or runs
 * it on the host's request.
 *
 * ```kotlin
 * startJetWhale { plugins { register(JetWhaleBackgroundWorkAgentPlugin.platformDefaults()) } }
 * ```
 *
 * An Android app on WorkManager adds it through the WorkManager adapter artifact:
 *
 * ```kotlin
 * JetWhaleBackgroundWorkAgentPlugin(
 *     sources = { BackgroundWorkSource.platformDefaults() + BackgroundWorkSource.workManager(WorkManager.getInstance(context)) },
 * )
 * ```
 *
 * [sources] is called when the host activates the plugin, so a scheduler that needs the app to be
 * up (WorkManager's instance, say) can be reached lazily.
 */
class JetWhaleBackgroundWorkAgentPlugin(
    private val sources: () -> List<BackgroundWorkSource>,
) : JetWhaleAgentPlugin() {
    override val pluginId: String get() = BACKGROUND_WORK_PLUGIN_ID
    override val pluginVersion: String get() = "1.0.0"

    private var activeSources: List<BackgroundWorkSource> = emptyList()

    // Non-null exactly while the host has this plugin activated.
    private var observationScope: CoroutineScope? = null

    override fun JetWhaleMessageHandlers.configure() {
        onRequest { _: GetBackgroundWork -> reply(observeSnapshots(activeSources).first()) }
        onRequest { request: CancelWork -> reply(operate(request.source) { it.cancel(request.target) }) }
        onRequest { request: RunWorkNow -> reply(operate(request.source) { it.runNow(request.id) }) }
    }

    override fun onActivate() {
        activeSources = sources()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        observationScope = scope
        scope.launch {
            observeSnapshots(activeSources).collect { snapshot: BackgroundWorkSnapshot ->
                messenger.trySend(BackgroundWorkChanged(snapshot))
            }
        }
    }

    override fun onDeactivate() {
        observationScope?.cancel()
        observationScope = null
    }

    private suspend fun operate(sourceName: String, action: suspend (BackgroundWorkSource) -> String): WorkOperationResult {
        val source = activeSources.firstOrNull { it.info.name == sourceName }
            ?: return WorkOperationResult(message = null, error = "no background work source is named '$sourceName'")
        return try {
            WorkOperationResult(message = action(source), error = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WorkOperationResult(message = null, error = e.message ?: e::class.simpleName ?: "the source refused")
        }
    }

    companion object {
        /** A plugin that reads [BackgroundWorkSource.platformDefaults]. */
        fun platformDefaults(): JetWhaleBackgroundWorkAgentPlugin = JetWhaleBackgroundWorkAgentPlugin(
            sources = BackgroundWorkSource::platformDefaults,
        )
    }
}
