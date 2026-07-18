package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineConfig

/**
 * Configures SSL settings for the HTTP client engine.
 *
 * @param sslConfiguration The SSL configuration containing trusted certificates.
 */
internal expect fun HttpClientEngineConfig.configureSsl(sslConfiguration: JetWhaleSslConfiguration)

/**
 * Disables server-certificate verification for the HTTP client engine.
 *
 * This is used only to fetch the host's CA certificate over the TLS (wss) port when the plain-HTTP
 * fetch is unreachable (e.g. a LAN device that cannot reach the loopback-bound plain server). It is
 * security-equivalent to fetching the CA over the unauthenticated plain channel: both are
 * trust-on-first-use, and the fetched CA still pins the subsequent wss session.
 *
 * Platforms whose engine cannot pin a custom CA in code (Web) also cannot honour this and keep their
 * default verification; the fetch simply fails there and the caller falls back.
 */
internal expect fun HttpClientEngineConfig.disableCertificateVerification()
