package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.curl.CurlClientEngineConfig

internal actual fun HttpClientEngineConfig.configureSsl(sslConfiguration: JetWhaleSslConfiguration) {
    if (sslConfiguration.trustedCertificates.isEmpty()) return

    check(this is CurlClientEngineConfig) { "Expected CurlClientEngineConfig but got ${this::class.simpleName}" }

    // Note: Curl engine requires certificate files on disk.
    // For custom certificates, users should configure the system's CA bundle
    // or provide certificates via environment variables (CURL_CA_BUNDLE).
    // Direct PEM string configuration is not supported by Curl engine.

    JetWhaleLogger.w(
        "SSL certificate configuration for Linux/Curl is limited. " +
            "Consider using system CA bundle or CURL_CA_BUNDLE environment variable.",
    )
}
