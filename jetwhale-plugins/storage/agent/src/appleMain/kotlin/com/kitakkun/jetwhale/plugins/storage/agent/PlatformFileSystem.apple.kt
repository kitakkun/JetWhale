package com.kitakkun.jetwhale.plugins.storage.agent

import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSFileCreationDate
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSFileTypeRegular
import platform.Foundation.NSFileTypeSymbolicLink
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.fileHandleForReadingAtPath
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.readDataOfLength
import platform.Foundation.seekToEndOfFile
import platform.Foundation.seekToFileOffset
import platform.Foundation.stringByResolvingSymlinksInPath
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeData
import platform.posix.errno
import platform.posix.memcpy
import platform.posix.rename
import platform.posix.strerror

@OptIn(ExperimentalForeignApi::class)
internal actual fun listDirectoryEntries(path: String): List<FileEntry> {
    val names = withNSError { error -> NSFileManager.defaultManager.contentsOfDirectoryAtPath(path, error) }
        ?: throw IllegalStateException("'$path' cannot be listed")
    val fileManager = NSFileManager.defaultManager
    return names.filterIsInstance<String>().map { name ->
        val childPath = "$path/$name"
        // attributesOfItemAtPath describes a symbolic link itself, not what it points to.
        val attributes = fileManager.attributesOfItemAtPath(childPath, null).orEmpty()
        val isDirectory = attributes[NSFileType] == NSFileTypeDirectory
        val isLink = attributes[NSFileType] == NSFileTypeSymbolicLink
        FileEntry(
            name = name,
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) 0 else (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: 0,
            lastModifiedEpochMillis = (attributes[NSFileModificationDate] as? NSDate)?.let { (it.timeIntervalSince1970 * 1000).toLong() },
            isSymbolicLink = isLink,
            linkTarget = if (isLink) fileManager.destinationOfSymbolicLinkAtPath(childPath, null) else null,
            createdEpochMillis = (attributes[NSFileCreationDate] as? NSDate)?.let { (it.timeIntervalSince1970 * 1000).toLong() },
            readable = fileManager.isReadableFileAtPath(childPath),
            writable = fileManager.isWritableFileAtPath(childPath),
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun readFileBytes(path: String, offset: Long, maxBytes: Int): ByteArray {
    val handle = NSFileHandle.fileHandleForReadingAtPath(path) ?: throw IllegalStateException("'$path' cannot be opened")
    val data = try {
        handle.seekToFileOffset(offset.toULong())
        handle.readDataOfLength(maxBytes.toULong())
    } finally {
        handle.closeFile()
    }
    val bytes = ByteArray(data.length.toInt())
    if (bytes.isNotEmpty()) {
        bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
    }
    return bytes
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun fileSize(path: String): Long {
    val attributes = withNSError { error -> NSFileManager.defaultManager.attributesOfItemAtPath(path, error) }
    return (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun writeFileBytes(path: String, bytes: ByteArray, append: Boolean) {
    val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    if (!append) {
        check(NSFileManager.defaultManager.createFileAtPath(path, contents = data, attributes = null)) { "'$path' could not be written" }
        return
    }
    val handle = NSFileHandle.fileHandleForWritingAtPath(path) ?: throw IllegalStateException("'$path' cannot be opened for writing")
    try {
        handle.seekToEndOfFile()
        handle.writeData(data)
    } finally {
        handle.closeFile()
    }
}

// rename(2) replaces a file atomically and refuses to put a file over a directory.
@OptIn(ExperimentalForeignApi::class)
internal actual fun moveReplacing(source: String, target: String) {
    check(rename(source, target) == 0) { "'$target' could not be replaced: ${strerror(errno)?.toKString()}" }
}

// removeItemAtPath deletes a symbolic link itself, never what it points to.
@OptIn(ExperimentalForeignApi::class)
internal actual fun deleteRecursively(path: String) {
    withNSError { error -> NSFileManager.defaultManager.removeItemAtPath(path, error) }
}

// attributesOfItemAtPath describes a link itself rather than its target.
@OptIn(ExperimentalForeignApi::class)
internal actual fun isSymbolicLink(path: String): Boolean = NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileType) == NSFileTypeSymbolicLink

@OptIn(ExperimentalForeignApi::class)
internal actual fun existsAsNonRegularFile(path: String): Boolean {
    val type = NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileType) ?: return false
    return type != NSFileTypeRegular
}

// stringByResolvingSymlinksInPath also drops a leading "/private", which is harmless: the root and
// the path are both resolved the same way before they are compared.
@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun resolvesInside(path: String, root: String): Boolean {
    val resolvedRoot = (root as NSString).stringByResolvingSymlinksInPath.trimEnd('/')
    val resolvedPath = (path as NSString).stringByResolvingSymlinksInPath
    return resolvedPath == resolvedRoot.ifEmpty { "/" } || resolvedPath.startsWith("$resolvedRoot/")
}

/** Runs a Foundation call that reports failure through an `NSError**`, and throws that error instead. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun <T> withNSError(call: (CPointer<ObjCObjectVar<NSError?>>) -> T): T = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    val result = call(error.ptr)
    error.value?.let { throw IllegalStateException(it.localizedDescription) }
    result
}
