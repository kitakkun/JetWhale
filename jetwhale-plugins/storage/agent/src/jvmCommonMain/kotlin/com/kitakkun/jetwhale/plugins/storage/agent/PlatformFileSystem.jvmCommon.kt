package com.kitakkun.jetwhale.plugins.storage.agent

import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile

internal actual fun listDirectoryEntries(path: String): List<FileEntry> {
    val directory = File(path)
    // listFiles() answers null both for a missing directory and for one the app may not read.
    val children = directory.listFiles() ?: throw IOException(
        if (directory.isDirectory) "'$path' cannot be read" else "'$path' is not a directory",
    )
    return children.map { child ->
        FileEntry(
            name = child.name,
            isDirectory = child.isDirectory,
            sizeBytes = if (child.isDirectory) 0 else child.length(),
            lastModifiedEpochMillis = child.lastModified().takeIf { it > 0 },
        )
    }
}

internal actual fun readFileBytes(path: String, offset: Long, maxBytes: Int): ByteArray {
    val file = File(path)
    if (!file.isFile) throw FileNotFoundException("'$path' is not a file")
    return RandomAccessFile(file, "r").use { access ->
        val length = (access.length() - offset).coerceIn(0, maxBytes.toLong()).toInt()
        ByteArray(length).also { buffer ->
            access.seek(offset)
            access.readFully(buffer)
        }
    }
}

internal actual fun fileSize(path: String): Long = File(path).length()

internal actual fun deleteRecursively(path: String) {
    val file = File(path)
    if (!file.exists()) throw FileNotFoundException("'$path' does not exist")
    if (!file.deleteRecursively()) throw IOException("'$path' could not be deleted completely")
}
