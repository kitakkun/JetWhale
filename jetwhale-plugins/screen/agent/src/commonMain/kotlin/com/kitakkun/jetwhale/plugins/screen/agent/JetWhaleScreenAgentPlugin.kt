package com.kitakkun.jetwhale.plugins.screen.agent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.plugins.screen.protocol.GrantFrameCredit
import com.kitakkun.jetwhale.plugins.screen.protocol.SCREEN_PLUGIN_ID
import com.kitakkun.jetwhale.plugins.screen.protocol.ScreenFrame
import com.kitakkun.jetwhale.plugins.screen.protocol.ScreenStreamStatus
import com.kitakkun.jetwhale.plugins.screen.protocol.StartScreenStream
import com.kitakkun.jetwhale.plugins.screen.protocol.StopScreenStream
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.reply
import com.kitakkun.jetwhale.protocol.messaging.trySend
import kotlin.time.Clock

/**
 * Agent plugin that streams the app's own windows to the host, captured from inside the app: no
 * ADB, no screen-recording permission, and nothing outside the app — not the system bars, not the
 * soft keyboard. Mark sensitive content with [maskedInScreenStream] to black it out on the device.
 *
 * ```kotlin
 * startJetWhale { plugins { register(JetWhaleScreenAgentPlugin()) } }
 * ```
 *
 * Captures on Android 10 (API 29) and later; elsewhere the host is told the stream is unsupported.
 */
class JetWhaleScreenAgentPlugin : JetWhaleAgentPlugin() {
    override val pluginId: String get() = SCREEN_PLUGIN_ID
    override val pluginVersion: String get() = "1.0.0"

    private val frameSource: ScreenFrameSource = platformScreenFrameSource()

    override fun JetWhaleMessageHandlers.configure() {
        onRequest { request: StartScreenStream -> reply(start(request)) }
        onRequest { _: StopScreenStream ->
            frameSource.stop()
            reply(ScreenStreamStatus(streaming = false, unsupportedReason = frameSource.unsupportedReason, agentEpochMillis = currentEpochMillis()))
        }
        onEvent { event: GrantFrameCredit -> frameSource.grant(event.count) }
    }

    private fun start(request: StartScreenStream): ScreenStreamStatus {
        val unsupported = frameSource.unsupportedReason
        if (unsupported != null) return ScreenStreamStatus(streaming = false, unsupportedReason = unsupported, agentEpochMillis = currentEpochMillis())
        frameSource.start(request) { frame -> messenger.trySend(frame) }
        return ScreenStreamStatus(streaming = true, unsupportedReason = null, agentEpochMillis = currentEpochMillis())
    }

    // A stream nobody is watching would keep copying and encoding windows for nothing.
    override suspend fun onDisconnected() {
        frameSource.stop()
    }

    override fun onDeactivate() {
        frameSource.stop()
    }
}

/** Captures the app's windows and hands encoded frames to the plugin. */
internal interface ScreenFrameSource {
    /** Why this platform cannot stream, or null when it can. */
    val unsupportedReason: String?

    /** Starts capturing with [settings], replacing any stream already running. */
    fun start(settings: StartScreenStream, onFrame: (ScreenFrame) -> Unit)

    /** Lets the source send [count] more frames; safe to call from any thread. */
    fun grant(count: Int)

    fun stop()
}

internal expect fun platformScreenFrameSource(): ScreenFrameSource

private fun currentEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
