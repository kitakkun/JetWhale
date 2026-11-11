package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.AiOperatedUiTargetsReporter
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * How long the UI keeps marking an AI agent's operation after it ends. Most tool calls finish in
 * tens of milliseconds, faster than the UI samples them, so an operation marked only while it runs
 * would often never show.
 */
val AI_OPERATION_INDICATOR_LINGER: Duration = 1500.milliseconds

/**
 * Tells a plugin instance's UI which of its UI targets an AI agent is operating, so the plugin can
 * mark the controls that stand for them. Fed by the plugin's MCP commands, through the reporter the
 * host runs each command with.
 */
@OptIn(InternalJetWhaleHostApi::class)
interface AiOperatedUiTargetsService {
    /** The reporter to run a command of [pluginId]'s instance in [sessionId] with. */
    fun reporterFor(pluginId: String, sessionId: String): AiOperatedUiTargetsReporter

    /**
     * The UI targets of [pluginId]'s instance in [sessionId] whose operation is running now or ended
     * less than [AI_OPERATION_INDICATOR_LINGER] ago. Emits again when a target's hold runs out.
     */
    fun operatedUiTargetsFlow(pluginId: String, sessionId: String): Flow<Set<Any>>
}
