package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes [directory] and everything below it into [zip], under a top folder named after the
 * directory. Files stream through a page at a time, so memory stays bounded however large they are.
 * A symbolic link is left out rather than followed, as the agent's measure and delete leave it.
 *
 * Stops at the first file or directory the agent cannot read and returns why, naming it: a ZIP that
 * silently lacks a file is worse for a bug report than no ZIP. [onFileZipped] receives the running
 * count of files written.
 */
internal suspend fun StorageClient.zipDirectory(directory: FileLocation, zip: ZipOutputStream, onFileZipped: (Int) -> Unit): String? = DirectoryZipper(client = this, zip = zip, onFileZipped = onFileZipped).write(directory, entryName = "${zipSafeSegment(directory.name)}/")

/** One ZIP being written: the client it reads through, the stream it writes to, and its file count. */
private class DirectoryZipper(
    private val client: StorageClient,
    private val zip: ZipOutputStream,
    private val onFileZipped: (Int) -> Unit,
) {
    private var filesZipped = 0

    /** Writes the directory at [location] as [entryName] (ending in "/"), then everything in it. */
    suspend fun write(location: FileLocation, entryName: String): String? {
        val listing = client.listDirectory(location)
        listing.error?.let { return "$entryName: $it" }
        zip.putNextEntry(ZipEntry(entryName))
        zip.closeEntry()
        for (entry in listing.entries.filterNot(FileEntry::isSymbolicLink)) {
            val child = location.child(entry.name)
            val name = zipSafeSegment(entry.name)
            val error = if (entry.isDirectory) write(child, "$entryName$name/") else writeFile(child, "$entryName$name", entry)
            if (error != null) return error
        }
        return null
    }

    private suspend fun writeFile(location: FileLocation, entryName: String, entry: FileEntry): String? {
        zip.putNextEntry(ZipEntry(entryName).apply { entry.lastModifiedEpochMillis?.let { time = it } })
        client.readWholeFile(location, zip::write)?.let { return "$entryName: $it" }
        zip.closeEntry()
        onFileZipped(++filesZipped)
        return null
    }
}

/**
 * [name] as one ZIP path segment. A root's name comes from the app and is not bound to be a single
 * plain segment, and an entry named "../x" or with a separator would make extraction write outside
 * the destination.
 */
internal fun zipSafeSegment(name: String): String {
    val replaced = name.replace('/', '_').replace('\\', '_').replace(':', '_')
    return if (replaced.isBlank() || replaced == "." || replaced == "..") "_" else replaced
}
