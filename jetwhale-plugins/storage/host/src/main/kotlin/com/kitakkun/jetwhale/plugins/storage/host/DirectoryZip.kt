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
internal suspend fun StorageClient.zipDirectory(directory: FileLocation, zip: ZipOutputStream, onFileZipped: (Int) -> Unit): String? = DirectoryZipper(client = this, zip = zip, onFileZipped = onFileZipped).writeDirectory(directory, zipEntryName = "${zipSafeSegment(directory.name)}/")

/** One ZIP being written: the client it reads through, the stream it writes to, and its file count. */
private class DirectoryZipper(
    private val client: StorageClient,
    private val zip: ZipOutputStream,
    private val onFileZipped: (Int) -> Unit,
) {
    private var filesZipped = 0

    /** Writes the directory at [location] as [zipEntryName] (ending in "/"), then everything in it. */
    suspend fun writeDirectory(location: FileLocation, zipEntryName: String): String? {
        val listing = client.listDirectory(location)
        listing.error?.let { return "$zipEntryName: $it" }
        zip.putNextEntry(ZipEntry(zipEntryName))
        zip.closeEntry()
        // ZipOutputStream throws on a repeated entry name.
        val usedSegments = mutableSetOf<String>()
        for (entry in listing.entries.filterNot(FileEntry::isSymbolicLink)) {
            val child = location.child(entry.name)
            val segment = zipSafeSegment(entry.name).let { safeSegment ->
                generateSequence(1, Int::inc).map { n -> if (n == 1) safeSegment else "$safeSegment ($n)" }.first(usedSegments::add)
            }
            val error = if (entry.isDirectory) writeDirectory(child, "$zipEntryName$segment/") else writeFile(child, "$zipEntryName$segment", entry)
            if (error != null) return error
        }
        return null
    }

    private suspend fun writeFile(location: FileLocation, zipEntryName: String, entry: FileEntry): String? {
        zip.putNextEntry(ZipEntry(zipEntryName).apply { entry.lastModifiedEpochMillis?.let { time = it } })
        client.readWholeFile(location, zip::write)?.let { return "$zipEntryName: $it" }
        zip.closeEntry()
        onFileZipped(++filesZipped)
        return null
    }
}

/**
 * [name] as one ZIP path segment. A root's name comes from the app and is not bound to be a single
 * plain segment, and an entry named "../x" or with a separator would make extraction write outside
 * the destination. A name of nothing but dots and spaces becomes `_` too, since Windows drops
 * trailing dots and spaces, which would leave ".. " as "..".
 */
internal fun zipSafeSegment(name: String): String {
    val replaced = name.replace('/', '_').replace('\\', '_').replace(':', '_')
    return if (replaced.all { it == '.' || it.isWhitespace() }) "_" else replaced
}
