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
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForReadingAtPath
import platform.Foundation.readDataOfLength
import platform.Foundation.seekToFileOffset
import platform.Foundation.timeIntervalSince1970
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal actual fun listDirectoryEntries(path: String): List<FileEntry> {
    val names = withNSError { error -> NSFileManager.defaultManager.contentsOfDirectoryAtPath(path, error) }
        ?: throw IllegalStateException("'$path' cannot be listed")
    return names.filterIsInstance<String>().map { name ->
        val attributes = NSFileManager.defaultManager.attributesOfItemAtPath("$path/$name", null).orEmpty()
        val isDirectory = attributes[NSFileType] == NSFileTypeDirectory
        FileEntry(
            name = name,
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) 0 else (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: 0,
            lastModifiedEpochMillis = (attributes[NSFileModificationDate] as? NSDate)?.let { (it.timeIntervalSince1970 * 1000).toLong() },
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

@OptIn(ExperimentalForeignApi::class)
internal actual fun deleteRecursively(path: String) {
    withNSError { error -> NSFileManager.defaultManager.removeItemAtPath(path, error) }
}

/** Runs a Foundation call that reports failure through an `NSError**`, and throws that error instead. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun <T> withNSError(call: (CPointer<ObjCObjectVar<NSError?>>) -> T): T = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    val result = call(error.ptr)
    error.value?.let { throw IllegalStateException(it.localizedDescription) }
    result
}
