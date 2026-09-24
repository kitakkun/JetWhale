package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryMeasurement
import com.kitakkun.jetwhale.plugins.storage.protocol.FileRootInfo
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

/**
 * What the detail pane says about the selected entry: [rows] of label and value, in the order they
 * are shown, holding only what is known — a fact the platform did not report is left out.
 *
 * @param root The root the entry is under, for its absolute path; null while the locations reload.
 * @param loadedFile The entry's previewed bytes, when it is a file that has been read.
 */
internal class EntryFacts(
    row: FileTreeRow,
    root: FileRootInfo?,
    val loadedFile: LoadedFile?,
    directoryMeasurement: DirectoryMeasurement?,
    fileSha256: String?,
) {
    val rows: List<Pair<String, String>> = buildList {
        val absolutePath = root?.let { (listOf(it.absolutePath.trimEnd('/')) + row.location.path).joinToString("/") }
        absolutePath?.let { add("Path" to it) }
        absolutePath?.let(::storedByOf)?.let { add("Stored by" to it) }
        loadedFile?.let { file -> fileKindOf(file.location.name, file.bytes)?.let { add("Kind" to it.label) } }
        loadedFile?.let(::textSummaryOf)?.let { add("Text" to it) }
        val entry = row.entry
        if (entry != null) {
            if (!entry.isDirectory) add("Size" to describeSize(entry.sizeBytes))
            if (entry.isSymbolicLink) add("Link" to (entry.linkTarget ?: "a symbolic link whose target could not be read"))
            entry.lastModifiedEpochMillis?.let { add("Modified" to TimestampFormatter.format(Instant.ofEpochMilli(it))) }
            entry.createdEpochMillis?.let { add("Created" to TimestampFormatter.format(Instant.ofEpochMilli(it))) }
            add("Access" to describeAccess(readable = entry.readable, writable = entry.writable))
        }
        directoryMeasurement?.let { measurement ->
            val floor = if (measurement.truncated) "at least " else ""
            add("Total size" to floor + describeSize(measurement.totalSizeBytes))
            add("Contents" to "$floor${count(measurement.fileCount, "file", "files")}, ${count(measurement.directoryCount, "directory", "directories")}")
        }
        fileSha256?.let { add("SHA-256" to it) }
    }
}

internal fun formatByteSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
}

private fun describeSize(bytes: Long): String = "${formatByteSize(bytes)} (${NumberFormat.getIntegerInstance(Locale.ROOT).format(bytes)} bytes)"

private fun describeAccess(readable: Boolean, writable: Boolean): String = when {
    readable && writable -> "Read and write"
    readable -> "Read only"
    writable -> "Write only"
    else -> "No access"
}

private fun count(value: Int, singular: String, plural: String): String = "$value ${if (value == 1) singular else plural}"
