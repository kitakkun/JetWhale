package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.data.cert.CACertificateGenerator
import com.kitakkun.jetwhale.host.data.cert.KeyPairFactory
import com.kitakkun.jetwhale.host.data.cert.ServerCertificateIssuer
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
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
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaintextCaCertificateRequestLogFilterTest {
    private val stdoutBefore = System.out
    private val capturedStdout = ByteArrayOutputStream()

    @AfterTest
    fun restoreStdout() {
        System.setOut(stdoutBefore)
    }

    // Netty logs a failed handshake after it has closed the connection, so the report shows only in
    // what logback wrote, and the test watches that output for it.
    @Suppress("KOTRAIL_TEST_REAL_TIME_WAIT")
    @Test
    fun `plaintext asking for the CA certificate on the wss port is not logged while other plaintext there still is`() = runBlocking {
        withStartedWssServerCapturingStdout { wssPort ->
            sendPlaintextAndAwaitClose(wssPort, "GET $CA_CERTIFICATE_URL_PATH HTTP/1.1\r\nHost: localhost\r\n\r\n")
            sendPlaintextAndAwaitClose(wssPort, "$WEB_SOCKET_UPGRADE_REQUEST_LINE\r\nHost: localhost\r\nUpgrade: websocket\r\n\r\n")
            awaitTlsHandshakeFailureReportOf(WEB_SOCKET_UPGRADE_REQUEST_LINE)
        }

        assertEquals(1, Regex(TLS_HANDSHAKE_FAILURE_REPORT).findAll(capturedStdout.toString()).count())
    }

    // Netty logs a failed handshake after it has closed the connection, so the report shows only in
    // what logback wrote, and the test watches that output for it.
    @Suppress("KOTRAIL_TEST_REAL_TIME_WAIT")
    @Test
    fun `plaintext that reaches the CA certificate request only after other bytes is still logged`() = runBlocking {
        val plaintextStart = "$MALFORMED_REQUEST_LINE\r\nGET $CA_CERTIFICATE_URL_PATH "
        withStartedWssServerCapturingStdout { wssPort ->
            sendPlaintextAndAwaitClose(wssPort, "${plaintextStart}HTTP/1.1\r\nHost: localhost\r\n\r\n")
            awaitTlsHandshakeFailureReportOf(plaintextStart)
        }

        assertEquals(1, Regex(TLS_HANDSHAKE_FAILURE_REPORT).findAll(capturedStdout.toString()).count())
    }

    private suspend fun withStartedWssServerCapturingStdout(block: suspend (wssPort: Int) -> Unit) {
        val server = KtorWebSocketServer(json = Json, negotiationStrategy = mock(), sslCertificateManager = mockSslCertificateManager())
        val wssPort = freePort()
        try {
            server.start(host = "localhost", port = freePort(), wssPort = wssPort)
            withTimeout(TIMEOUT_MILLIS) { server.statusFlow.first { it is DebugWebSocketServerStatus.Started } }
            System.setOut(PrintStream(capturedStdout, true))
            block(wssPort)
        } finally {
            server.stop()
        }
    }

    private suspend fun awaitTlsHandshakeFailureReportOf(plaintextStart: String) {
        val plaintextStartHex = plaintextStart.encodeToByteArray().toHexString()
        withTimeout(TIMEOUT_MILLIS) {
            while (plaintextStartHex !in capturedStdout.toString()) delay(20)
        }
    }

    private fun sendPlaintextAndAwaitClose(port: Int, request: String) {
        Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
            socket.getOutputStream().write(request.encodeToByteArray())
            socket.getInputStream().readAllBytes()
        }
    }

    private fun mockSslCertificateManager(): SslCertificateManager {
        val ca = CACertificateGenerator().createRootCA(commonName = "JetWhale Test CA")
        val serverKeyPair = KeyPairFactory().generate()
        val serverCertificate = ServerCertificateIssuer().issue(ca = ca, serverKeyPair = serverKeyPair)
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry(KEY_ALIAS, serverKeyPair.private, KEY_STORE_PASSWORD.toCharArray(), arrayOf(serverCertificate, ca.cert))
        }
        return mock<SslCertificateManager> {
            every { certificatesFlow } returns MutableStateFlow(emptyList())
            every { hasCertificate() } returns true
            every { getActiveKeyStore() } returns keyStore
            every { getActiveKeyAlias() } returns KEY_ALIAS
            every { getKeyStorePassword() } returns KEY_STORE_PASSWORD.toCharArray()
        }
    }

    private fun freePort(): Int = ServerSocket(0).use(ServerSocket::getLocalPort)

    private companion object {
        const val TLS_HANDSHAKE_FAILURE_REPORT = "TLS handshake failed"
        const val WEB_SOCKET_UPGRADE_REQUEST_LINE = "GET / HTTP/1.1"
        const val MALFORMED_REQUEST_LINE = "NOT A REQUEST"
        const val TIMEOUT_MILLIS = 10_000L
        const val KEY_ALIAS = "test_alias"
        const val KEY_STORE_PASSWORD = "test_pass"
    }
}
