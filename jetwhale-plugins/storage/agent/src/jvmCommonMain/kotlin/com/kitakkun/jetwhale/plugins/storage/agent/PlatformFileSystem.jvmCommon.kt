package com.kitakkun.jetwhale.plugins.storage.agent

import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

internal actual fun listDirectoryEntries(path: String): List<FileEntry> {
    val directory = File(path)
    // listFiles() answers null both for a missing directory and for one the app may not read.
    val children = directory.listFiles() ?: throw IOException(
        if (directory.isDirectory) "'$path' cannot be read" else "'$path' is not a directory",
    )
    return children.map { child ->
        val isLink = child.isSymbolicLink()
        FileEntry(
            name = child.name,
            isDirectory = child.isDirectory,
            sizeBytes = if (child.isDirectory) 0 else child.length(),
            lastModifiedEpochMillis = child.lastModified().takeIf { it > 0 },
            isSymbolicLink = isLink,
            // The resolved target rather than the link's own text, which java.io cannot read.
            linkTarget = if (isLink) child.canonicalPath else null,
            createdEpochMillis = createdEpochMillis(child),
            readable = child.canRead(),
            writable = child.canWrite(),
        )
    }
}

/** When [file] was created, or null where the platform cannot say. */
internal expect fun createdEpochMillis(file: File): Long?

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

internal actual fun writeFileBytes(path: String, bytes: ByteArray, append: Boolean) {
    FileOutputStream(path, append).use { it.write(bytes) }
}

internal actual fun moveReplacing(source: String, target: String) {
    val targetFile = File(target)
    if (targetFile.isDirectory) throw IOException("'$target' is a directory, not a file to replace")
    if (File(source).renameTo(targetFile)) return
    // POSIX systems replace the target atomically above; Windows refuses to rename over a file, so
    // the target is moved aside first and put back if the replacement still fails.
    val backup = File("$source.replaced")
    if (!targetFile.renameTo(backup)) throw IOException("'$target' could not be replaced")
    if (!File(source).renameTo(targetFile)) {
        backup.renameTo(targetFile)
        throw IOException("'$target' could not be replaced")
    }
    backup.delete()
}

internal actual fun deleteRecursively(path: String) {
    val file = File(path)
    // exists() follows links, so a dangling link would read as missing.
    if (!file.exists() && !file.isSymbolicLink()) throw FileNotFoundException("'$path' does not exist")
    deleteWithoutFollowingLinks(file)
}

internal actual fun isSymbolicLink(path: String): Boolean = File(path).isSymbolicLink()

internal actual fun isDirectory(path: String): Boolean = File(path).isDirectory

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
