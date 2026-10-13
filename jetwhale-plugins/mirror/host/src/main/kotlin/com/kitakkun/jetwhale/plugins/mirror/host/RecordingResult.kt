package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.serialization.json.JsonObject
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

/** The result as the recording tools report it: the device, what happened, and the file or the reason. */
internal fun RecordingResult.toJson(): JsonObject = buildJsonObject {
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
            put("error", reason)
        }
    }
}
