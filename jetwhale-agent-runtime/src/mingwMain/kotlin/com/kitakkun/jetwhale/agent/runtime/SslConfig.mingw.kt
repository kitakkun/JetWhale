package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.winhttp.WinHttpClientEngineConfig

internal actual fun HttpClientEngineConfig.configureSsl(sslConfiguration: JetWhaleSslConfiguration) {
    if (sslConfiguration.trustedCertificates.isEmpty()) return

    // The engine is not user-configurable: it always comes from defaultKtorEngineFactory(), which
    // returns WinHttp on this platform. If the default engine ever changes, fail fast here instead of
    // silently skipping certificate pinning (which would surface as an obscure TLS handshake error).
    check(this is WinHttpClientEngineConfig) { "Expected WinHttpClientEngineConfig but got ${this::class.simpleName}" }

    // WinHttp exposes no per-connection CA configuration (only sslVerify on/off); it always
    // validates against the Windows certificate store. In-code pinning is therefore not possible
    // without installing the CA into the store, which is left to the user to keep this library from
    // mutating machine state.
    JetWhaleLogger.w(
        "WinHttp validates certificates against the Windows certificate store and cannot pin a custom CA in code. " +
            "Export the JetWhale CA certificate from the desktop app and install it into the store, e.g.: " +
            "certutil -user -addstore Root jetwhale-ca.pem",
    )
}
