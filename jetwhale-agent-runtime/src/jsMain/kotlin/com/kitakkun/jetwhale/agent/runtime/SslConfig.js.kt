package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineConfig

internal actual fun HttpClientEngineConfig.configureSsl(sslConfiguration: JetWhaleSslConfiguration) {
    if (sslConfiguration.trustedCertificates.isEmpty()) return

    // Note: Browser-based JavaScript environments manage SSL certificates automatically.
    // Custom certificate configuration is not supported in browser environments.
    // The browser's built-in certificate store is used for SSL verification.

    JetWhaleLogger.w(
        "SSL certificate configuration is not supported in JavaScript environments. " +
            "The browser manages SSL certificates automatically.",
    )
}
