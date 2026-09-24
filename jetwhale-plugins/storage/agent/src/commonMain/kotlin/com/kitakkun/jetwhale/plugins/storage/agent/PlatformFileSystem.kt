package com.kitakkun.jetwhale.plugins.storage.agent

import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry

internal expect fun listDirectoryEntries(path: String): List<FileEntry>

internal expect fun readFileBytes(path: String, offset: Long, maxBytes: Int): ByteArray

internal expect fun fileSize(path: String): Long

/** Deletes [path] and, for a directory, everything in it. A symbolic link is deleted, never followed. */
internal expect fun deleteRecursively(path: String)

/** True when [path] is [root] or lies below it once every symbolic link in both is resolved. */
internal expect fun resolvesInside(path: String, root: String): Boolean
