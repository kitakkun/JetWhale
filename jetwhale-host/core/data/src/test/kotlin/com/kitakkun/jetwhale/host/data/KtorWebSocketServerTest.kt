package com.kitakkun.jetwhale.host.data

import com.kitakkun.jetwhale.host.data.cert.CACertificateGenerator
import com.kitakkun.jetwhale.host.data.cert.KeyPairFactory
import com.kitakkun.jetwhale.host.data.cert.ServerCertificateIssuer
import com.kitakkun.jetwhale.host.data.server.KtorWebSocketServer
import com.kitakkun.jetwhale.host.data.ssl.DefaultSslCertificateManager
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.SslCertificateEntry
import com.kitakkun.jetwhale.host.model.SslCertificateManager
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class KtorWebSocketServerTest {
    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun `starts with plain ws connector only`() {
        val server = KtorWebSocketServer(
            json = Json,
            negotiationStrategy = mock(),
            sslCertificateManager = mock(),
        )
        runBlocking {
            server.start("localhost", freePort(), wssPort = null)
            server.stop()
        }
    }

    @Test
    fun `starts wss connector backed by the active certificate`() {
        val ca = CACertificateGenerator().createRootCA(commonName = "JetWhale Test CA")
        val serverKeyPair = KeyPairFactory().generate()
        val serverCertificate = ServerCertificateIssuer().issue(ca = ca, serverKeyPair = serverKeyPair)
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("test_alias", serverKeyPair.private, "test_pass".toCharArray(), arrayOf(serverCertificate, ca.cert))
        }
        val sslCertificateManager = mock<SslCertificateManager> {
            every { certificatesFlow } returns MutableStateFlow(emptyList())
            every { hasCertificate() } returns true
            every { getActiveKeyStore() } returns keyStore
            every { getActiveKeyAlias() } returns "test_alias"
            every { getKeyStorePassword() } returns "test_pass".toCharArray()
        }

        val server = KtorWebSocketServer(
            json = Json,
            negotiationStrategy = mock(),
            sslCertificateManager = sslCertificateManager,
        )

        val wssPort = freePort()
        runBlocking {
            server.start("localhost", freePort(), wssPort = wssPort)
            val status = withTimeout(10_000) {
                server.statusFlow.first { it is DebugWebSocketServerStatus.Started }
            }
            assertEquals(wssPort, (status as DebugWebSocketServerStatus.Started).wssPort)
            server.stop()
        }
    }

    @Test
    fun `swapping the active certificate restarts the tls listener with the new certificate`() {
        // A real certificate manager backed by a temp home so activating a new certificate emits on
        // certificatesFlow, which the server observes to hot-swap the TLS listener.
        val tempHome = createTempDirectory().toFile()
        val originalHome = System.getProperty("user.home")
        System.setProperty("user.home", tempHome.absolutePath)
        try {
            val sslCertificateManager = DefaultSslCertificateManager(AppDataDirectoryProvider())
            sslCertificateManager.generateAndAddCertificate("first")

            val server = KtorWebSocketServer(
                json = Json,
                negotiationStrategy = mock(),
                sslCertificateManager = sslCertificateManager,
            )

            val wssPort = freePort()
            runBlocking {
                server.start("localhost", freePort(), wssPort = wssPort)
                withTimeout(10_000) {
                    server.statusFlow.first { it is DebugWebSocketServerStatus.Started }
                }

                val serialBefore = withTimeout(10_000) {
                    var serial: BigInteger? = null
                    while (serial == null) {
                        serial = runCatching { fetchLeafSerial(wssPort) }.getOrNull()
                        if (serial == null) delay(100)
                    }
                    serial
                }

                // Generating a new certificate marks it active, which the server hot-swaps onto the
                // wss listener without touching the plain server.
                sslCertificateManager.generateAndAddCertificate("second")

                val serialAfter = withTimeout(10_000) {
                    var serial = serialBefore
                    while (serial == serialBefore) {
                        delay(100)
                        serial = runCatching { fetchLeafSerial(wssPort) }.getOrDefault(serialBefore)
                    }
                    serial
                }

                assertNotEquals(serialBefore, serialAfter)
                server.stop()
            }
        } finally {
            System.setProperty("user.home", originalHome)
            tempHome.deleteRecursively()
        }
    }

    @Test
    fun `serves the active CA certificate over the plain channel`() {
        val caCertificatePem = "-----BEGIN CERTIFICATE-----\nTESTCADATA\n-----END CERTIFICATE-----"
        val sslCertificateManager = mock<SslCertificateManager> {
            every { getActiveKeyAlias() } returns null
            every { getActiveCertificate() } returns SslCertificateEntry(
                id = "test-id",
                name = "Test CA",
                createdAt = 0L,
                caCertificatePem = caCertificatePem,
                isActive = true,
            )
        }

        val server = KtorWebSocketServer(
            json = Json,
            negotiationStrategy = mock(),
            sslCertificateManager = sslCertificateManager,
        )

        val port = freePort()
        runBlocking {
            server.start("localhost", port, wssPort = null)
            withTimeout(10_000) {
                server.statusFlow.first { it is DebugWebSocketServerStatus.Started }
            }

            val connection = URI("http://localhost:$port/jetwhale/ca").toURL().openConnection() as HttpURLConnection
            try {
                assertEquals(HttpURLConnection.HTTP_OK, connection.responseCode)
                assertEquals(caCertificatePem, connection.inputStream.bufferedReader().readText())
            } finally {
                connection.disconnect()
            }

            server.stop()
        }
    }

    @Test
    fun `serves the active CA certificate over the tls channel`() {
        // A real certificate manager backed by a temp home so the TLS server starts with a usable
        // keystore and getActiveCertificate() returns the matching CA PEM.
        val tempHome = createTempDirectory().toFile()
        val originalHome = System.getProperty("user.home")
        System.setProperty("user.home", tempHome.absolutePath)
        try {
            val sslCertificateManager = DefaultSslCertificateManager(AppDataDirectoryProvider())
            sslCertificateManager.generateAndAddCertificate("tls-ca")
            val expectedPem = sslCertificateManager.getActiveCertificate()?.caCertificatePem

            val server = KtorWebSocketServer(
                json = Json,
                negotiationStrategy = mock(),
                sslCertificateManager = sslCertificateManager,
            )

            val wssPort = freePort()
            runBlocking {
                server.start("localhost", freePort(), wssPort = wssPort)
                withTimeout(10_000) {
                    server.statusFlow.first { it is DebugWebSocketServerStatus.Started }
                }

                val body = withTimeout(10_000) {
                    var result: String? = null
                    while (result == null) {
                        result = runCatching { httpsGet(wssPort, "/jetwhale/ca") }.getOrNull()
                        if (result == null) delay(100)
                    }
                    result
                }

                assertEquals(expectedPem, body)
                server.stop()
            }
        } finally {
            System.setProperty("user.home", originalHome)
            tempHome.deleteRecursively()
        }
    }

    /** Performs an HTTPS GET against the TLS listener with verification disabled. */
    private fun httpsGet(port: Int, path: String): String {
        val trustAll = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            },
        )
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
        val connection = URI("https://localhost:$port$path").toURL().openConnection() as HttpsURLConnection
        connection.sslSocketFactory = sslContext.socketFactory
        connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
        return try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "unexpected status ${connection.responseCode}" }
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Opens a TLS handshake against the wss listener with a trust-all manager and returns the serial
     * number of the presented leaf certificate.
     */
    private fun fetchLeafSerial(port: Int): BigInteger {
        val trustAll = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            },
        )
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
        val socket = sslContext.socketFactory.createSocket("127.0.0.1", port) as SSLSocket
        return socket.use {
            it.startHandshake()
            (it.session.peerCertificates[0] as X509Certificate).serialNumber
        }
    }
}
