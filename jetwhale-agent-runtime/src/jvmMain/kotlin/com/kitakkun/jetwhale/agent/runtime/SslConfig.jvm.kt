package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.cio.CIOEngineConfig
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal actual fun HttpClientEngineConfig.configureSsl(sslConfiguration: JetWhaleSslConfiguration) {
    if (sslConfiguration.trustedCertificates.isEmpty()) return

    check(this is CIOEngineConfig) { "Expected CIOEngineConfig but got ${this::class.simpleName}" }

    https {
        trustManager = createTrustManager(sslConfiguration.trustedCertificates)
    }
}

private fun createTrustManager(pemCertificates: List<String>): X509TrustManager {
    val certificateFactory = CertificateFactory.getInstance("X.509")
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null, null)
    }

    pemCertificates.forEachIndexed { index, pem ->
        val certificate = certificateFactory.generateCertificate(
            ByteArrayInputStream(pem.toByteArray())
        ) as X509Certificate
        keyStore.setCertificateEntry("trusted_cert_$index", certificate)
    }

    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagerFactory.init(keyStore)

    return trustManagerFactory.trustManagers
        .filterIsInstance<X509TrustManager>()
        .first()
}
