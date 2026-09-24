package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.plugins.storage.protocol.MAX_FILE_READ_BYTES
import java.util.UUID

/**
 * Writes [totalSizeBytes] bytes to [location] in chunks of at most [MAX_FILE_READ_BYTES], taking
 * each from [readChunk] and reporting the bytes sent so far to [onProgress]. The agent replaces the
 * file only once the last chunk arrives. Returns the agent's error when a chunk is refused; no chunk
 * follows it.
 */
internal suspend fun StorageClient.writeWholeFile(
    location: FileLocation,
    totalSizeBytes: Long,
    readChunk: (offset: Long, size: Int) -> ByteArray,
    onProgress: (sentBytes: Long) -> Unit,
): String? {
    val uploadId = UUID.randomUUID().toString()
    var offset = 0L
    // An empty file is still one chunk: the last one, which creates it.
    do {
        val chunk = readChunk(offset, minOf(MAX_FILE_READ_BYTES.toLong(), totalSizeBytes - offset).toInt())
        writeFileChunk(location, uploadId = uploadId, offset = offset, bytes = chunk, isLast = offset + chunk.size >= totalSizeBytes).error?.let { return it }
        offset += chunk.size
        onProgress(offset)
    } while (offset < totalSizeBytes)
    return null
}
