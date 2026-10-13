package com.kitakkun.jetwhale.host.data.plugin

import java.io.File
import java.security.MessageDigest

/** The SHA-256 of this file's bytes, as lowercase hex. Blocking: call it off the main thread. */
internal fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { stream ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
