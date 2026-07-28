package com.kitakkun.jetwhale.host.data.util

import java.io.File

/**
 * Finds the absolute path to the idb client executable (Facebook's iOS debug bridge, used to
 * drive iOS simulator input), checking common installation locations and then the PATH.
 *
 * @return The absolute path to idb if found, otherwise an empty string
 */
fun findIdbPath(): String = findExecutable(
    name = "idb",
    candidatePaths = listOf(
        "/usr/local/bin/idb",
        "/opt/homebrew/bin/idb",
    ),
)

/**
 * Finds the absolute path to the idb_companion executable (the gRPC daemon the idb client talks
 * to; installed separately, typically via `brew install idb-companion`).
 *
 * @return The absolute path to idb_companion if found, otherwise an empty string
 */
fun findIdbCompanionPath(): String = findExecutable(
    name = "idb_companion",
    candidatePaths = listOf(
        "/usr/local/bin/idb_companion",
        "/opt/homebrew/bin/idb_companion",
    ),
)

private fun findExecutable(name: String, candidatePaths: List<String>): String {
    candidatePaths.firstOrNull { File(it).canExecute() }?.let { return it }
    val pathDirs = System.getenv("PATH")?.split(File.pathSeparator).orEmpty()
    return pathDirs.map { File(it, name) }.firstOrNull { it.canExecute() }?.absolutePath.orEmpty()
}
