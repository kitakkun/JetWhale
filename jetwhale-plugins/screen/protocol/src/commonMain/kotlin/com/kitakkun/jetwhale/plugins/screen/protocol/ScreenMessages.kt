package com.kitakkun.jetwhale.plugins.screen.protocol

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleEvent
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The pluginId shared by the Screen Stream agent and host plugins. */
const val SCREEN_PLUGIN_ID: String = "com.kitakkun.jetwhale.screen"

/**
 * Starts streaming the app's own windows. Frames are sent only while the host holds credit: each
 * [ScreenFrame] spends one, and [GrantFrameCredit] returns them, so a slow link or a slow decoder
 * makes the agent skip redraws instead of queueing them.
 *
 * @property scale The frame's size relative to the screen, in (0, 1].
 * @property jpegQuality JPEG quality, 1..100.
 * @property maxFramesPerSecond The most frames a second the agent sends, however often the app redraws.
 * @property initialCredits How many frames the agent may send before the first [GrantFrameCredit].
 */
@SerialName("screen/start")
@Serializable
data class StartScreenStream(
    val scale: Float,
    val jpegQuality: Int,
    val maxFramesPerSecond: Int,
    val initialCredits: Int,
) : JetWhaleRequest<ScreenStreamStatus>

@SerialName("screen/stop")
@Serializable
data object StopScreenStream : JetWhaleRequest<ScreenStreamStatus>

/**
 * Reply to [StartScreenStream] and [StopScreenStream]. [unsupportedReason] is set when the platform
 * cannot capture.
 *
 * @property agentEpochMillis The agent's clock when it replied. A device's clock can be seconds off
 *   the host's, an emulator's included, so the host uses this to put frame times on its own clock.
 */
@SerialName("screen/status")
@Serializable
data class ScreenStreamStatus(
    val streaming: Boolean,
    val unsupportedReason: String?,
    val agentEpochMillis: Long,
)

/** Lets the agent send [count] more frames. */
@SerialName("screen/grant_credit")
@Serializable
data class GrantFrameCredit(val count: Int) : JetWhaleEvent

/**
 * One captured picture of the app's windows, composited as they sit on screen.
 *
 * @property jpegBase64 The frame as a JPEG, Base64-encoded.
 */
@SerialName("screen/frame")
@Serializable
data class ScreenFrame(
    val sequence: Long,
    val widthPx: Int,
    val heightPx: Int,
    val jpegBase64: String,
    val timing: FrameTiming,
) : JetWhaleEvent

/**
 * Where the time of one frame went on the agent, for judging the cost of streaming.
 *
 * @property captureStartedEpochMillis When the agent started copying the windows; the host measures
 *   latency from here.
 * @property mainThreadMicros Time the capture spent on the app's main thread.
 * @property pixelCopyMillis From the copy request to the last window's pixels arriving, scaled.
 * @property composeMillis Placing the windows into one frame and masking it.
 * @property compressMillis JPEG compression.
 * @property base64Millis Base64-encoding the JPEG for the JSON message.
 * @property sentEpochMillis When the frame was handed to the messenger.
 */
@Serializable
data class FrameTiming(
    val captureStartedEpochMillis: Long,
    val mainThreadMicros: Long,
    val pixelCopyMillis: Long,
    val composeMillis: Long,
    val compressMillis: Long,
    val base64Millis: Long,
    val sentEpochMillis: Long,
)
