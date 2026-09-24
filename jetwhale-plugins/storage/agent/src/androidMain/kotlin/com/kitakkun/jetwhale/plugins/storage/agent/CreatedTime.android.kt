package com.kitakkun.jetwhale.plugins.storage.agent

import android.os.Build
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

// java.nio.file, the only way to read a creation time, exists from API 26.
internal actual fun createdEpochMillis(file: File): Long? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    return try {
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).creationTime().toMillis()
    } catch (_: IOException) {
        null
    }
}
