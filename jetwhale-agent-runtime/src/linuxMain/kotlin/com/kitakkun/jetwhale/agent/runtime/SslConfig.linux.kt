package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.curl.CurlClientEngineConfig
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.O_CREAT
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.S_IRUSR
import platform.posix.S_IWUSR
import platform.posix.close
import platform.posix.getenv
import platform.posix.getpid
import platform.posix.open
import platform.posix.write

internal actual fun HttpClientEngineConfig.disableCertificateVerification() {
    check(this is CurlClientEngineConfig) { "Expected CurlClientEngineConfig but got ${this::class.simpleName}" }
    // Curl validates the peer against the CA bundle by default; disabling it lets the CA fetch
    // succeed over the wss port (trust-on-first-use). The fetched CA still pins the wss session.
    sslVerify = false
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun HttpClientEngineConfig.configureSsl(sslConfiguration: JetWhaleSslConfiguration) {
    if (sslConfiguration.trustedCertificates.isEmpty()) return

    // The engine is not user-configurable: it always comes from defaultKtorEngineFactory(), which
    // returns Curl on this platform. If the default engine ever changes, fail fast here instead of
    // silently skipping certificate pinning (which would surface as an obscure TLS handshake error).
    check(this is CurlClientEngineConfig) { "Expected CurlClientEngineConfig but got ${this::class.simpleName}" }

    // Curl only accepts CA material as a file (CURLOPT_CAINFO), so the configured PEMs are written
    // to a private per-process bundle file and pinned via caInfo.
    val bundlePath = writeCaBundle(sslConfiguration.trustedCertificates)
    if (bundlePath == null) {
        JetWhaleLogger.w("Failed to write the trusted CA bundle; falling back to system trust evaluation.")
        return
    }

    caInfo = bundlePath
}

/**
 * Writes the PEM certificates as one bundle file under the temporary directory with owner-only
 * permissions. Returns null when the file cannot be created.
 */
@OptIn(ExperimentalForeignApi::class)
private fun writeCaBundle(pemCertificates: List<String>): String? {
    val tmpDir = getenv("TMPDIR")?.toKString()?.trimEnd('/') ?: "/tmp"
    val path = "$tmpDir/jetwhale_ca_${getpid()}.pem"
    val fd = open(path, O_WRONLY or O_CREAT or O_TRUNC, (S_IRUSR or S_IWUSR).toUInt())
    if (fd < 0) return null
    try {
        val bytes = pemCertificates.joinToString("\n").encodeToByteArray()
        var offset = 0
        while (offset < bytes.size) {
            val written = bytes.usePinned { pinned ->
                write(fd, pinned.addressOf(offset), (bytes.size - offset).toULong())
            }
            if (written < 0) return null
            offset += written.toInt()
        }
    } finally {
        close(fd)
    }
    return path
}
