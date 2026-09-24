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
    // exists() follows links, so a dangling link would read as missing.
    if (!file.exists() && !file.isSymbolicLink()) throw FileNotFoundException("'$path' does not exist")
    deleteWithoutFollowingLinks(file)
}

internal actual fun resolvesInside(path: String, root: String): Boolean {
    val canonicalRoot = File(root).canonicalFile
    return generateSequence(File(path).canonicalFile, File::getParentFile).any { it == canonicalRoot }
}

// java.nio.file would say this directly, but Android has it only from API 26.
private fun deleteWithoutFollowingLinks(file: File) {
    if (file.isDirectory && !file.isSymbolicLink()) file.listFiles()?.forEach(::deleteWithoutFollowingLinks)
    if (!file.delete()) throw IOException("'${file.path}' could not be deleted")
}

/** True when the last component of this path is a symbolic link, whether or not its target exists. */
private fun File.isSymbolicLink(): Boolean {
    val parent = absoluteFile.parentFile?.canonicalFile ?: return false
    val inCanonicalParent = File(parent, name)
    return inCanonicalParent.canonicalFile != inCanonicalParent.absoluteFile
}
