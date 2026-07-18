package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.darwin.DarwinClientEngineConfig

internal actual fun HttpClientEngineConfig.configureSsl(sslConfiguration: JetWhaleSslConfiguration) {
    if (sslConfiguration.trustedCertificates.isEmpty()) return

    check(this is DarwinClientEngineConfig) { "Expected DarwinClientEngineConfig but got ${this::class.simpleName}" }

    // Note: Darwin engine uses the system's certificate store by default.
    // Custom certificate configuration for Darwin requires NSURLSession delegate setup
    // which is currently not fully supported through Ktor's Darwin engine configuration.
    // For custom certificates on Apple platforms, consider adding them to the system keychain
    // or using a custom NSURLSessionDelegate.

    JetWhaleLogger.w(
        "SSL certificate configuration for Apple/Darwin is limited. " +
            "Consider adding certificates to the system keychain.",
    )
}
