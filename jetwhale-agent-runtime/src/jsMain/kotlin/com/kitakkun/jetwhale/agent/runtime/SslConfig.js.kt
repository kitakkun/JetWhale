package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineConfig

internal actual fun HttpClientEngineConfig.disableCertificateVerification() {
    JetWhaleLogger.w(
        "Certificate verification cannot be disabled in JavaScript environments; " +
            "the CA fetch over the wss port is not available.",
    )
}

internal actual fun HttpClientEngineConfig.configureSsl(sslConfiguration: JetWhaleSslConfiguration) {
    if (sslConfiguration.trustedCertificates.isEmpty()) return

    JetWhaleLogger.w(
        "SSL certificate configuration is not supported in JavaScript environments. " +
            "The browser manages SSL certificates automatically.",
    )
}
