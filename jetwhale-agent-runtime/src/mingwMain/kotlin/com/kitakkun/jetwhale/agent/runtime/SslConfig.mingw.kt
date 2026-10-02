package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.winhttp.WinHttpClientEngineConfig

internal actual fun HttpClientEngineConfig.disableCertificateVerification() {
    check(this is WinHttpClientEngineConfig) { "Expected WinHttpClientEngineConfig but got ${this::class.simpleName}" }
    sslVerify = false
}

internal actual fun HttpClientEngineConfig.configureSsl(sslConfiguration: JetWhaleSslConfiguration) {
    if (sslConfiguration.trustedCertificates.isEmpty()) return

    check(this is WinHttpClientEngineConfig) { "Expected WinHttpClientEngineConfig but got ${this::class.simpleName}" }

    JetWhaleLogger.w(
        "WinHttp validates certificates against the Windows certificate store and cannot pin a custom CA in code. " +
            "Export the JetWhale CA certificate from the desktop app and install it into the store, e.g.: " +
            "certutil -user -addstore Root jetwhale-ca.pem",
    )
}
