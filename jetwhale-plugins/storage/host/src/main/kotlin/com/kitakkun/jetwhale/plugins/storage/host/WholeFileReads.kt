package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.plugins.storage.protocol.MAX_FILE_READ_BYTES
import java.security.MessageDigest
import kotlin.io.encoding.Base64

/**
 * Reads the whole file at [location] from the agent a page at a time, handing each page to
 * [onPage] in order. Returns the agent's error when a read fails; no page follows it.
 */
internal suspend fun StorageClient.readWholeFile(location: FileLocation, onPage: (ByteArray) -> Unit): String? {
    var offset = 0L
    while (true) {
        val page = readFile(location, offset = offset, maxBytes = MAX_FILE_READ_BYTES)
        page.error?.let { return it }
        val bytes = Base64.decode(page.contentBase64)
        onPage(bytes)
        offset += bytes.size
        if (bytes.isEmpty() || offset >= page.totalSizeBytes) return null
    }
}

/** The SHA-256 of a file as lowercase hex, or the error that kept it from being read. */
internal class FileDigest(val sha256Hex: String?, val error: String?)

/**
 * Hashes the file at [location] here in the host, from the pages [readWholeFile] fetches. Hashing on
 * the agent would send only the digest, but it would need a SHA-256 implementation on every agent
 * platform, the web included; reading pages works everywhere the file can be read at all.
 */
internal suspend fun StorageClient.sha256Of(location: FileLocation): FileDigest {
    val digest = MessageDigest.getInstance("SHA-256")
    val error = readWholeFile(location, digest::update)
    return if (error != null) FileDigest(sha256Hex = null, error = error) else FileDigest(sha256Hex = digest.digest().toHexString(), error = null)
}
