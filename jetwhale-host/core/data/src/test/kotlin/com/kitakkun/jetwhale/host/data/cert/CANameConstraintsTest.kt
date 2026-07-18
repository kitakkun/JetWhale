package com.kitakkun.jetwhale.host.data.cert

import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CANameConstraintsTest {
    private val caGenerator = CACertificateGenerator()
    private val issuer = ServerCertificateIssuer()
    private val keyPairFactory = KeyPairFactory()

    private fun validateChain(server: X509Certificate, ca: CaMaterial) {
        val certFactory = CertificateFactory.getInstance("X.509")
        // Include the CA certificate in the path (rather than as the trust anchor) and anchor on the
        // CA's name/key. Java's PKIX applies name constraints carried by CA certificates in the path
        // but not those embedded in a trust-anchor certificate, so this exercises the real extension.
        val certPath = certFactory.generateCertPath(listOf(server, ca.cert))
        val anchor = TrustAnchor(ca.cert.subjectX500Principal, ca.keyPair.public, null)
        val params = PKIXParameters(setOf(anchor)).apply {
            // Local PKI has no CRL/OCSP infrastructure; disable revocation checks.
            isRevocationEnabled = false
        }
        CertPathValidator.getInstance("PKIX").validate(certPath, params)
    }

    @Test
    fun `server certificate with private-range SAN validates under the name-constrained CA`() {
        val ca = caGenerator.createRootCA(commonName = "JetWhale Local CA")
        val server = issuer.issue(
            ca = ca,
            serverKeyPair = keyPairFactory.generate(),
            commonName = "localhost",
            dnsSans = listOf("localhost"),
            ipSans = listOf("127.0.0.1", "192.168.1.50"),
        )

        validateChain(server, ca)
    }

    @Test
    fun `server certificate with a public DNS SAN is rejected by the name-constrained CA`() {
        val ca = caGenerator.createRootCA(commonName = "JetWhale Local CA")
        val server = issuer.issue(
            ca = ca,
            serverKeyPair = keyPairFactory.generate(),
            commonName = "example.com",
            dnsSans = listOf("example.com"),
            ipSans = emptyList(),
        )

        assertFailsWith<CertPathValidatorException> {
            validateChain(server, ca)
        }
    }
}
