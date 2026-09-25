package com.kitakkun.jetwhale.host.data.plugin

import java.io.File

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
    private var reported: Map<String, JarStamp> = stamps()
    private var lastSeen: Map<String, JarStamp> = reported

    /** Absolute paths of the jars whose settled state differs from the one last reported. */
    fun poll(): Set<String> {
        val current = stamps()
        val settledChanges = (current.keys + lastSeen.keys + reported.keys)
            .filter { path -> current[path] == lastSeen[path] && current[path] != reported[path] }
            .toSet()
        reported = reported.filterKeys { it !in settledChanges } + current.filterKeys(settledChanges::contains)
        lastSeen = current
        return settledChanges
    }

    private fun stamps(): Map<String, JarStamp> = directory.listFiles { file -> file.isFile && file.extension == "jar" }
        .orEmpty()
        .associate { it.absolutePath to JarStamp(sizeBytes = it.length(), lastModifiedMillis = it.lastModified()) }

    private data class JarStamp(val sizeBytes: Long, val lastModifiedMillis: Long)
}
