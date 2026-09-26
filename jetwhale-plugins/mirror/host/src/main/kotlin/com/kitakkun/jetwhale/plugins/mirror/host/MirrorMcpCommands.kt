package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Shared by the mirror's MCP command classes (one class per file in this package).

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

    /** Stops the running recording and adds it to the captures. */
    suspend fun stopRecording(): Capture

    fun listCaptures(deviceId: String?, kind: CaptureKind?, sinceEpochMillis: Long?): List<Capture>
}

internal fun okJson(): String = buildJsonObject { put("ok", true) }.toString()

/** Runs a device operation for a tool, turning a refusal into an answer the caller can act on. */
@OptIn(ExperimentalJetWhaleApi::class)
internal suspend fun <T> deviceOperation(operation: suspend () -> T): T = try {
    operation()
} catch (e: DeviceControlException) {
    throw JetWhaleMcpArgumentException(e.message.orEmpty())
}
