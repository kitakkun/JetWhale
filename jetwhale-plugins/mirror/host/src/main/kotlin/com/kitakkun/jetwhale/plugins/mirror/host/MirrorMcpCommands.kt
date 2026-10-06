package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val TOOL_PREFIX = "com.kitakkun.jetwhale.mirror"

internal const val DEVICE_ID_DESCRIPTION = "Device id from $TOOL_PREFIX.listDevices. Omit it to use the device selected in the mirror."

/** What the MCP commands can see and do; [DeviceMirror] is the real one. */
internal interface MirrorDevices {
    val selectedId: String?

    suspend fun refresh(): List<MirrorDevice>

    /** The device [deviceId] names, or the selected one when it is null. */
    fun resolve(deviceId: String?): MirrorDevice

    /** Saves a screenshot of [device] into the captures. */
    suspend fun saveScreenshot(device: MirrorDevice): Capture

    suspend fun startRecording(device: MirrorDevice)

    /**
     * Starts recording each of [deviceIds] at once, or, when it is null, every device that can record
     * and is not recording yet. A device that fails to start leaves the others recording.
     */
    suspend fun startRecordings(deviceIds: List<String>?): List<RecordingResult>

    /**
     * Stops [deviceId]'s recording and adds it to the captures. Null stops the one recording running,
     * and is refused while several run.
     */
    suspend fun stopRecording(deviceId: String?): Capture

    /** Stops each of [deviceIds]' recordings at once, or every running one when it is null. */
    suspend fun stopRecordings(deviceIds: List<String>?): List<RecordingResult>

    fun listCaptures(deviceId: String?, kind: CaptureKind?, sinceEpochMillis: Long?): List<Capture>
}

@OptIn(ExperimentalJetWhaleApi::class)
internal fun okResult(): JetWhaleMcpResult = JetWhaleMcpResult.json(buildJsonObject { put("ok", true) })

/** Runs a device operation for a tool, turning a refusal into an answer the caller can act on. */
@OptIn(ExperimentalJetWhaleApi::class)
internal suspend fun <T> deviceOperation(operation: suspend () -> T): T = try {
    operation()
} catch (e: DeviceControlException) {
    throw JetWhaleMcpArgumentException(e.message.orEmpty())
}
