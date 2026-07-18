package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.darwin.DarwinClientEngineConfig
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import platform.CoreFoundation.CFArrayCreate
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.kCFAllocatorDefault
import platform.Foundation.NSData
import platform.Foundation.NSDataBase64DecodingIgnoreUnknownCharacters
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.create
import platform.Foundation.serverTrust
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecCertificateRef
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustSetAnchorCertificates
import platform.Security.SecTrustSetAnchorCertificatesOnly

@OptIn(ExperimentalForeignApi::class)
internal actual fun HttpClientEngineConfig.disableCertificateVerification() {
    check(this is DarwinClientEngineConfig) { "Expected DarwinClientEngineConfig but got ${this::class.simpleName}" }
    handleChallenge { _, _, challenge, completionHandler ->
        val serverTrust = challenge.protectionSpace.serverTrust
        if (challenge.protectionSpace.authenticationMethod != NSURLAuthenticationMethodServerTrust || serverTrust == null) {
            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
            return@handleChallenge
        }
        // Accept the presented server trust unconditionally: this path only fetches the CA over the
        // wss port (trust-on-first-use), and the fetched CA still pins the subsequent wss session.
        completionHandler(NSURLSessionAuthChallengeUseCredential, NSURLCredential.create(trust = serverTrust))
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun HttpClientEngineConfig.configureSsl(sslConfiguration: JetWhaleSslConfiguration) {
    if (sslConfiguration.trustedCertificates.isEmpty()) return

    // The engine is not user-configurable: it always comes from defaultKtorEngineFactory(), which
    // returns Darwin on this platform. If the default engine ever changes, fail fast here instead of
    // silently skipping certificate pinning (which would surface as an obscure TLS handshake error).
    check(this is DarwinClientEngineConfig) { "Expected DarwinClientEngineConfig but got ${this::class.simpleName}" }

    val anchorCertificates = sslConfiguration.trustedCertificates.mapNotNull { pem ->
        pemToSecCertificate(pem).also {
            if (it == null) JetWhaleLogger.w("Failed to parse a trusted certificate PEM; it will be ignored.")
        }
    }
    if (anchorCertificates.isEmpty()) {
        JetWhaleLogger.w("No trusted certificate could be parsed; falling back to system trust evaluation.")
        return
    }

    handleChallenge { _, _, challenge, completionHandler ->
        val serverTrust = challenge.protectionSpace.serverTrust
        if (challenge.protectionSpace.authenticationMethod != NSURLAuthenticationMethodServerTrust || serverTrust == null) {
            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
            return@handleChallenge
        }

        // Evaluate the server chain against the configured CA certificates only, so the locally
        // issued JetWhale CA is trusted without being installed in the device trust store.
        val trusted = memScoped {
            val certArray = allocArrayOf(*anchorCertificates.toTypedArray())
            val cfAnchors = CFArrayCreate(kCFAllocatorDefault, certArray.reinterpret(), anchorCertificates.size.convert(), null)
            SecTrustSetAnchorCertificates(serverTrust, cfAnchors)
            SecTrustSetAnchorCertificatesOnly(serverTrust, true)
            SecTrustEvaluateWithError(serverTrust, null)
        }

        if (trusted) {
            completionHandler(NSURLSessionAuthChallengeUseCredential, NSURLCredential.create(trust = serverTrust))
        } else {
            JetWhaleLogger.w("Server certificate is not signed by a trusted JetWhale CA; cancelling the connection.")
            completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
        }
    }
}

/** Decodes a PEM certificate (base64 DER between BEGIN/END markers) into a [SecCertificateRef]. */
@OptIn(ExperimentalForeignApi::class)
private fun pemToSecCertificate(pem: String): SecCertificateRef? {
    val base64 = pem.lineSequence()
        .filterNot { it.contains("-----") }
        .joinToString("")
        .trim()
    val derData = NSData.create(
        base64EncodedString = base64,
        options = NSDataBase64DecodingIgnoreUnknownCharacters,
    ) ?: return null
    val cfData = CFDataCreate(
        kCFAllocatorDefault,
        derData.bytes?.reinterpret<UByteVar>(),
        derData.length.convert(),
    ) ?: return null
    return SecCertificateCreateWithData(null, cfData)
}
