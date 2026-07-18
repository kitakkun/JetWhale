package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineConfig

internal actual fun HttpClientEngineConfig.disableCertificateVerification() {
    // The browser fully controls TLS verification; it cannot be disabled from code. The CA fetch
    // over the wss port therefore fails here and the caller falls back to plain ws.
    JetWhaleLogger.w(
        "Certificate verification cannot be disabled in WebAssembly environments; " +
            "the CA fetch over the wss port is not available.",
    )
}

internal actual fun HttpClientEngineConfig.configureSsl(sslConfiguration: JetWhaleSslConfiguration) {
    if (sslConfiguration.trustedCertificates.isEmpty()) return

    // Note: Browser-based WebAssembly environments manage SSL certificates automatically.
    // Custom certificate configuration is not supported in browser environments.
    // The browser's built-in certificate store is used for SSL verification.

    JetWhaleLogger.w(
        "SSL certificate configuration is not supported in WebAssembly environments. " +
            "The browser manages SSL certificates automatically.",
    )
}
