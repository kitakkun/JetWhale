package com.kitakkun.jetwhale.plugins.storage.agent

import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryMeasurement
import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry

internal expect fun listDirectoryEntries(path: String): List<FileEntry>

internal expect fun readFileBytes(path: String, offset: Long, maxBytes: Int): ByteArray

internal expect fun fileSize(path: String): Long

/** Deletes [path] and, for a directory, everything in it. A symbolic link is deleted, never followed. */
internal expect fun deleteRecursively(path: String)

/** Writes [bytes] to [path], replacing what is there or, with [append], after it. The parent must exist. */
internal expect fun writeFileBytes(path: String, bytes: ByteArray, append: Boolean)

/** Moves the file at [source] to [target], replacing a file already there. A directory is never replaced. */
internal expect fun moveReplacing(source: String, target: String)

/**
 * Takes one chunk of an upload: collects it in [stagingPath] and, on the [isLast] chunk, moves the
 * staged file over [targetPath]. Any failure removes the staged file, so a broken upload leaves
 * [targetPath] as it was and has to start again from offset 0.
 */
internal fun receiveUploadChunk(stagingPath: String, targetPath: String, offset: Long, bytes: ByteArray, isLast: Boolean) {
    // Refused before the try: the cleanup below may only ever remove a plain file this upload
    // created, never a link (writing would follow it) or a directory that happens to have the name.
    require(!isSymbolicLink(stagingPath)) { "the upload's staging file is a symbolic link" }
    require(!isDirectory(stagingPath)) { "the upload's staging path is an existing directory" }
    try {
        if (offset != 0L) {
            val received = fileSize(stagingPath)
            require(received == offset) { "the upload expected a chunk at offset $received, not $offset; start it again" }
        }
        writeFileBytes(stagingPath, bytes, append = offset != 0L)
        if (isLast) moveReplacing(stagingPath, targetPath)
    } catch (e: Exception) {
        runCatching { deleteRecursively(stagingPath) }
        throw e
    }
}

/** True when [path] is [root] or lies below it once every symbolic link in both is resolved. */
internal expect fun resolvesInside(path: String, root: String): Boolean

/** True when [path] itself is a symbolic link, looked at without following it. */
internal expect fun isSymbolicLink(path: String): Boolean

internal expect fun isDirectory(path: String): Boolean

/**
 * Adds up [path] and everything below it, breadth first. A symbolic link counts as one file of no
 * size and is never followed. The walk stops after [entryLimit] entries and reports what it has
 * counted so far as truncated.
 */
internal fun measureDirectoryTree(path: String, entryLimit: Int): DirectoryMeasurement {
    var totalSizeBytes = 0L
    var fileCount = 0
    var directoryCount = 0
    if (isSymbolicLink(path)) return DirectoryMeasurement(totalSizeBytes = 0, fileCount = 1, directoryCount = 0, truncated = false, error = null)
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
