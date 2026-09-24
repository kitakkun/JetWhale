package com.kitakkun.jetwhale.plugins.storage.agent

import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryMeasurement
import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry

internal expect fun listDirectoryEntries(path: String): List<FileEntry>

internal expect fun readFileBytes(path: String, offset: Long, maxBytes: Int): ByteArray

internal expect fun fileSize(path: String): Long

/** Deletes [path] and, for a directory, everything in it. A symbolic link is deleted, never followed. */
internal expect fun deleteRecursively(path: String)

/** True when [path] is [root] or lies below it once every symbolic link in both is resolved. */
internal expect fun resolvesInside(path: String, root: String): Boolean

/**
 * Adds up [path] and everything below it, breadth first. A symbolic link counts as one file of no
 * size and is never followed. The walk stops after [entryLimit] entries and reports what it has
 * counted so far as truncated.
 */
internal fun measureDirectoryTree(path: String, entryLimit: Int): DirectoryMeasurement {
    var totalSizeBytes = 0L
    var fileCount = 0
    var directoryCount = 0
    val pending = ArrayDeque(listOf(path))
    while (pending.isNotEmpty()) {
        val directory = pending.removeFirst()
        for (entry in listDirectoryEntries(directory)) {
            if (fileCount + directoryCount >= entryLimit) {
                return DirectoryMeasurement(totalSizeBytes, fileCount, directoryCount, truncated = true, error = null)
            }
            if (entry.isDirectory && !entry.isSymbolicLink) {
                directoryCount++
                pending += "$directory/${entry.name}"
            } else {
                fileCount++
                if (!entry.isSymbolicLink) totalSizeBytes += entry.sizeBytes
            }
        }
    }
    return DirectoryMeasurement(totalSizeBytes, fileCount, directoryCount, truncated = false, error = null)
}
