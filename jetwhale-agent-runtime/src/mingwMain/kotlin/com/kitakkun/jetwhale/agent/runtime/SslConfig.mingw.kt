package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.winhttp.WinHttpClientEngineConfig

internal actual fun HttpClientEngineConfig.configureSsl(sslConfiguration: JetWhaleSslConfiguration) {
    if (sslConfiguration.trustedCertificates.isEmpty()) return

    check(this is WinHttpClientEngineConfig) { "Expected WinHttpClientEngineConfig but got ${this::class.simpleName}" }

    // Note: WinHttp uses Windows certificate store.
    // For custom certificates, users should install them in the Windows certificate store.
    // Direct PEM string configuration is not supported by WinHttp engine.

    JetWhaleLogger.w("SSL certificate configuration for Windows/WinHttp is limited. " +
            "Consider installing certificates in the Windows certificate store.")
}
