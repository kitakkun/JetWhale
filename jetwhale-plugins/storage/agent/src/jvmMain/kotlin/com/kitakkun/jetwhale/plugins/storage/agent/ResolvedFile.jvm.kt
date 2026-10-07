package com.kitakkun.jetwhale.plugins.storage.agent

import java.io.File
import java.io.IOException

// Not canonicalFile: before JDK 24, Windows canonical paths leave symbolic links unresolved.
internal actual fun resolvedFile(file: File): File = try {
    file.toPath().toRealPath().toFile()
} catch (_: IOException) {
    // toRealPath fails not only for a missing path but also for a link that loops or leads into a
    // directory the app cannot search.
    file.canonicalFile
}
