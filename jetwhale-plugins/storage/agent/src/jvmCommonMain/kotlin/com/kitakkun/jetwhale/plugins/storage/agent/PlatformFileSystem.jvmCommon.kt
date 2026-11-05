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
    val resolvedDirectory = resolvedFile(directory)
    return children.map { child ->
        val isLink = child.isSymbolicLinkIn(resolvedDirectory)
        FileEntry(
            name = child.name,
            isDirectory = child.isDirectory,
            sizeBytes = if (child.isDirectory) 0 else child.length(),
            lastModifiedEpochMillis = child.lastModified().takeIf { it > 0 },
            isSymbolicLink = isLink,
            // The resolved target rather than the link's own text, which java.io cannot read.
            linkTarget = if (isLink) resolvedFile(child).path else null,
            createdEpochMillis = createdEpochMillis(child),
            readable = child.canRead(),
            writable = child.canWrite(),
        )
    }
}

/** When [file] was created, or null where the platform cannot say. */
internal expect fun createdEpochMillis(file: File): Long?

/**
 * [file] as an absolute path with every symbolic link in it resolved. A path that does not exist yet
 * is resolved through the part of it that does, so a file about to be created shows where a link
 * above it leads. A path that cannot be resolved, because a link in it loops or leads where the app
 * may not look, comes back in its canonical form instead; on Windows before JDK 24 that form can
 * still go through a link.
 */
internal expect fun resolvedFile(file: File): File

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
    // File.renameTo fails on Windows when the target exists, so there the target is moved aside
    // first.
    val backup = File("$source.replaced")
    if (!targetFile.renameTo(backup)) throw IOException("'$target' could not be replaced")
    if (!File(source).renameTo(targetFile)) {
        if (!backup.renameTo(targetFile)) throw IOException("'$target' could not be replaced, and its previous content could not be put back; it is at '${backup.path}'")
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

internal actual fun existsAsNonRegularFile(path: String): Boolean = File(path).let { it.exists() && !it.isFile }

internal actual fun resolvesInside(path: String, root: String): Boolean {
    val resolvedRoot = resolvedFile(File(root))
    return generateSequence(resolvedFile(File(path)), File::getParentFile).any { it == resolvedRoot }
}

// java.nio.file would say this directly, but Android has it only from API 26.
private fun deleteWithoutFollowingLinks(file: File) {
    if (file.isDirectory && !file.isSymbolicLink()) file.listFiles()?.forEach(::deleteWithoutFollowingLinks)
    if (!file.delete()) throw IOException("'${file.path}' could not be deleted")
}

/** True when the last component of this path is a symbolic link, whether or not its target exists. */
private fun File.isSymbolicLink(): Boolean = isSymbolicLinkIn(resolvedFile(absoluteFile.parentFile ?: return false))

/** True when this file, a child of the directory whose resolved form is [resolvedParent], is itself a symbolic link. */
private fun File.isSymbolicLinkIn(resolvedParent: File): Boolean {
    // Not Files.isSymbolicLink: java.nio.file arrives on Android only at API 26, and this code runs
    // down to API 23.
    val inResolvedParent = File(resolvedParent, name)
    if (resolvedFile(inResolvedParent) != inResolvedParent.absoluteFile) return true
    // A link to a missing target cannot be resolved and comes back as it is, and exists() follows
    // the link, so a listed name that does not exist can only be such a link.
    return !exists() && resolvedParent.list().orEmpty().contains(name)
}
