package com.kitakkun.jetwhale.host.data.plugin

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

/**
 * Reports the jars directly in [directory] that were added, changed or removed, once they have
 * stopped changing: an entry is reported only after it reads the same — the same size and
 * modification time, or absent — on two polls in a row. A jar still being copied or downloaded is
 * therefore not read half-written, and a jar replaced by a delete-then-copy is seen as one change
 * rather than a removal followed by an addition.
 *
 * The jars present on construction are the baseline and are not reported.
 */
internal class PluginJarDirectoryPoller(private val directory: File) {
    private var reported: Map<String, JarStamp> = stamps().orEmpty()
    private var lastSeen: Map<String, JarStamp> = reported

    /**
     * Absolute paths of the jars whose settled state differs from the one last reported. A poll
     * whose listing fails reports nothing and leaves the state as it was: an unreadable directory is
     * not an empty one, and taking it for empty would unload every plugin.
     */
    fun poll(): Set<String> {
        val current = stamps() ?: return emptySet()
        val settledChanges = (current.keys + lastSeen.keys + reported.keys)
            .filter { path -> current[path] == lastSeen[path] && current[path] != reported[path] }
            .toSet()
        reported = reported.filterKeys { it !in settledChanges } + current.filterKeys(settledChanges::contains)
        lastSeen = current
        return settledChanges
    }

    /**
     * Makes [jarPath] be reported again at the next poll where it has settled, for a change the caller
     * could not handle; otherwise the change would count as reported and never be retried.
     */
    fun redeliver(jarPath: String) {
        reported = reported + (jarPath to UNHANDLED)
    }

    /** The jars in [directory], or null when it cannot be listed. */
    private fun stamps(): Map<String, JarStamp>? = directory.listFiles { file -> file.isFile && file.extension == "jar" }
        ?.associate { it.absolutePath to JarStamp(sizeBytes = it.length(), lastModifiedMillis = it.lastModified(), fileKey = fileKeyOf(it)) }

    /**
     * The file system's identity for [file] (an inode on POSIX), which changes when a file is replaced
     * by a rename even if its size and modification time were kept; null where the platform has none.
     */
    private fun fileKeyOf(file: File): Any? = try {
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java).fileKey()
    } catch (_: IOException) {
        null
    }

    private data class JarStamp(val sizeBytes: Long, val lastModifiedMillis: Long, val fileKey: Any?)

    private companion object {
        /** Differs from every real stamp and from absence, so the path always reads as changed. */
        val UNHANDLED = JarStamp(sizeBytes = -1, lastModifiedMillis = -1, fileKey = null)
    }
}
