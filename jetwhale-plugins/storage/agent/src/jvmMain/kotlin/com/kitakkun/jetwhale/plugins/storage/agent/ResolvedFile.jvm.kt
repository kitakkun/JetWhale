package com.kitakkun.jetwhale.plugins.storage.agent

import java.io.File
import java.io.IOException
import java.nio.file.NoSuchFileException

// Not canonicalFile: before JDK 24, Windows canonical paths leave symbolic links unresolved.
internal actual fun resolvedFile(file: File): File = try {
    file.toPath().toRealPath().toFile()
} catch (_: NoSuchFileException) {
    val parent = file.absoluteFile.parentFile
    if (parent == null) file.canonicalFile else File(resolvedFile(parent), file.name)
} catch (_: IOException) {
    // toRealPath fails not only for a missing path but also for a link that loops or leads into a
    // directory the app cannot search.
    file.canonicalFile
}
