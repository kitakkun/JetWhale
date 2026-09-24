package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal enum class CaptureKind(val label: String, val suffix: String, val extension: String) {
    Screenshot("Screenshot", "screenshot", "png"),
    Recording("Recording", "recording", "mp4"),
}

/**
 * What is known about one capture, kept in a JSON file beside it so the list can be rebuilt from
 * the folder alone.
 *
 * @property deviceId The device's serial or UDID: stable across sessions, unlike its name.
 * @property durationMillis How long a recording runs; null for a screenshot.
 */
@Serializable
internal data class CaptureInfo(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val deviceKind: String,
    val osVersion: String?,
    val kind: CaptureKind,
    val widthPx: Int?,
    val heightPx: Int?,
    val capturedAtEpochMillis: Long,
    val durationMillis: Long?,
)

internal data class Capture(val file: File, val info: CaptureInfo)

private val SidecarJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

private val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss")

/**
 * Screenshots and recordings, one folder per device under [root]:
 * `<device name>-<short id>/<yyyy-MM-dd>/<HHmmss>-screenshot.png`, each with a `.json` sidecar.
 * The short id is derived from the serial or UDID, so a device keeps its folder across sessions
 * and two devices with the same name never share one.
 */
internal class CaptureLibrary(val root: File, private val zone: ZoneId) {
    fun deviceFolder(device: DeviceListing): File = File(root, "${safeName(device.name)}-${shortId(device.id)}")

    /** A file for a new capture of [device] taken at [at], not yet written. */
    fun newFile(device: DeviceListing, kind: CaptureKind, at: Instant): File {
        val time = at.atZone(zone)
        val day = File(deviceFolder(device), DayFormat.format(time)).apply { mkdirs() }
        val base = "${TimeFormat.format(time)}-${kind.suffix}"
        return generateSequence(1) { it + 1 }
            .map { n -> File(day, if (n == 1) "$base.${kind.extension}" else "$base-$n.${kind.extension}") }
            .first { !it.exists() }
    }

    /** Writes [info] beside [file], which completes the capture. */
    fun record(file: File, info: CaptureInfo): Capture {
        sidecarOf(file).writeText(SidecarJson.encodeToString(CaptureInfo.serializer(), info))
        return Capture(file, info)
    }

    /**
     * Every capture whose file is still there, newest first, narrowed to [deviceId], [kind] and
     * captures taken at or after [sinceEpochMillis] when those are given.
     */
    fun list(deviceId: String?, kind: CaptureKind?, sinceEpochMillis: Long?): List<Capture> = root.walk()
        .maxDepth(CAPTURE_DEPTH)
        .filter { it.isFile && it.name.endsWith(SIDECAR_SUFFIX) }
        .mapNotNull(::readCapture)
        .filter { deviceId == null || it.info.deviceId == deviceId }
        .filter { kind == null || it.info.kind == kind }
        .filter { sinceEpochMillis == null || it.info.capturedAtEpochMillis >= sinceEpochMillis }
        .sortedByDescending { it.info.capturedAtEpochMillis }
        .toList()

    /** Removes the capture, its sidecar and its thumbnail. */
    fun delete(capture: Capture) {
        capture.file.delete()
        sidecarOf(capture.file).delete()
        thumbnailFileOf(capture.file).delete()
    }

    /** Where the thumbnail of [file] is cached, in a hidden folder beside the day's captures. */
    fun thumbnailFileOf(file: File): File = File(File(file.parentFile, THUMBNAILS), "${file.name}.png")

    // A sidecar whose capture was deleted by hand, or that cannot be read, is not a capture.
    private fun readCapture(sidecar: File): Capture? {
        val file = File(sidecar.path.removeSuffix(SIDECAR_SUFFIX))
        if (!file.isFile) return null
        return try {
            Capture(file, SidecarJson.decodeFromString(CaptureInfo.serializer(), sidecar.readText()))
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun sidecarOf(file: File): File = File(file.path + SIDECAR_SUFFIX)
}

private const val SIDECAR_SUFFIX = ".json"
private const val THUMBNAILS = ".thumbnails"

/** Device folder, day folder, capture: sidecars are never deeper. */
private const val CAPTURE_DEPTH = 3

/** Enough of a SHA-256 to tell a machine's devices apart; the name in front carries the meaning. */
private const val SHORT_ID_LENGTH = 8

private fun shortId(id: String): String = MessageDigest.getInstance("SHA-256")
    .digest(id.toByteArray())
    .joinToString("") { "%02x".format(it) }
    .take(SHORT_ID_LENGTH)

// Letters, digits, dots, dashes and underscores survive on every file system; the rest become '-'.
private fun safeName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifEmpty { "device" }

/**
 * Where captures go unless the user picks a folder: beside the host's own data for this plugin,
 * which the host keeps under `jetwhale.appDataDir` (a sandbox for development launches) or
 * `~/.jetwhale`.
 */
internal fun defaultCapturesRoot(): File {
    val appData = System.getProperty("jetwhale.appDataDir")?.takeIf(String::isNotBlank) ?: "${System.getProperty("user.home")}/.jetwhale"
    return File(appData, "plugin-data/com.kitakkun.jetwhale.mirror/captures")
}
