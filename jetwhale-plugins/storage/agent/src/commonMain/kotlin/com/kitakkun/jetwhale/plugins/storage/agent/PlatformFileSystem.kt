package com.kitakkun.jetwhale.plugins.storage.agent

import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry

internal expect fun listDirectoryEntries(path: String): List<FileEntry>

internal expect fun readFileBytes(path: String, offset: Long, maxBytes: Int): ByteArray

internal expect fun fileSize(path: String): Long

internal expect fun deleteRecursively(path: String)
