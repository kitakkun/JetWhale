package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal const val TOOL_PREFIX = "com.kitakkun.jetwhale.mirror"

internal fun okJson(): String = buildJsonObject { put("ok", true) }.toString()

/** Where saved device screenshots land, both for the UI button and the MCP tool. */
internal fun screenshotFile(device: MirrorDevice): File = captureFile(device, extension = "png")

/** Where saved screen recordings land, both for the UI button and the MCP tool. */
internal fun recordingFile(device: MirrorDevice): File = captureFile(device, extension = "mp4")

private fun captureFile(device: MirrorDevice, extension: String): File {
    val dir = File(System.getProperty("java.io.tmpdir"), "jetwhale-mirror").apply { mkdirs() }
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
    return File(dir, "${device.platform.name.lowercase()}-$timestamp.$extension")
}
