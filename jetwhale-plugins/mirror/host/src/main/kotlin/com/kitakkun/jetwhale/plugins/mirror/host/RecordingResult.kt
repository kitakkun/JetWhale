package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** How starting or stopping one device's recording went. */
internal sealed interface RecordingResult {
    val deviceId: String
    val deviceName: String

    data class Started(override val deviceId: String, override val deviceName: String) : RecordingResult

    data class Saved(val capture: Capture) : RecordingResult {
        override val deviceId: String get() = capture.info.deviceId
        override val deviceName: String get() = capture.info.deviceName
    }

    data class Failed(override val deviceId: String, override val deviceName: String, val reason: String) : RecordingResult
}

/**
 * Each device's result as the recording tools report it. The call fails only when no device
 * succeeded: after a partial success, retrying the whole call would act again on the devices that did.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal fun List<RecordingResult>.toMcpResult(): JetWhaleMcpResult {
    val report = buildJsonObject { put("results", buildJsonArray { forEach { add(it.toJson()) } }) }
    return if (isNotEmpty() && all { it is RecordingResult.Failed }) JetWhaleMcpResult.error(report.toString()) else JetWhaleMcpResult.json(report)
}

private fun RecordingResult.toJson(): JsonObject = buildJsonObject {
    put("deviceId", deviceId)
    put("deviceName", deviceName)
    when (this@toJson) {
        is RecordingResult.Started -> put("status", "started")

        is RecordingResult.Saved -> {
            put("status", "stopped")
            put("path", capture.file.absolutePath)
            capture.info.durationMillis?.let { put("durationMillis", it) }
        }

        is RecordingResult.Failed -> {
            put("status", "failed")
            put("reason", reason)
        }
    }
}
