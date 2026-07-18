package com.kitakkun.jetwhale.host.data

import com.kitakkun.jetwhale.host.data.cert.CACertificateGenerator
import com.kitakkun.jetwhale.host.data.cert.KeyPairFactory
import com.kitakkun.jetwhale.host.data.cert.ServerCertificateIssuer
import com.kitakkun.jetwhale.host.data.server.KtorWebSocketServer
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.SslCertificateManager
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.net.ServerSocket
import java.security.KeyStore
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
