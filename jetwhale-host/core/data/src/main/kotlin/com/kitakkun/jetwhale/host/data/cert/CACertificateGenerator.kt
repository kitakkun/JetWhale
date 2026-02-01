package com.kitakkun.jetwhale.host.data.cert

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.util.io.pem.PemObject
import java.io.StringWriter
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

data class CaMaterial(
    val keyPair: KeyPair,
    val cert: X509Certificate,
)

class CACertificateGenerator {
    fun createRootCA(
        commonName: String,
        daysValid: Int = 3650,
    ): CaMaterial {
        // Java built-in provider does not support some algorithms required to generate CA certificate.
        // So we use BouncyCastle as a security provider.
        if (Security.getProvider(CertificateSpec.PROVIDER) == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        // Generate key pair
        val keyPairGenerator = KeyPairGenerator.getInstance(CertificateSpec.ALGORITHM, CertificateSpec.PROVIDER)
        keyPairGenerator.initialize(256)
        val cakeyPair = keyPairGenerator.generateKeyPair()

        val now = Date()
        val expirationDate = Date(now.time + daysValid * 24L * 60L * 60L * 1000L)

        val subject = X500Name("CN=$commonName")

        val certHolder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(160, SecureRandom()).abs(),
            now,
            expirationDate,
            subject,
            cakeyPair.public,
        ).addExtension(
            // Needed to mark this certificate as CA
            Extension.basicConstraints,
            true,
            BasicConstraints(true),
        ).addExtension(
            // restrict key usage for CA
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign),
        ).addExtension(
            // specify public hash for identifying certificate
            Extension.subjectKeyIdentifier,
            false,
            JcaX509ExtensionUtils().createSubjectKeyIdentifier(cakeyPair.public),
        ).build(
            // sign the certificate with private key
            JcaContentSignerBuilder(CertificateSpec.SIGNATURE_ALGORITHM)
                .setProvider(CertificateSpec.PROVIDER)
                .build(cakeyPair.private)
        )

        // convert to X509Certificate
        val caCert = JcaX509CertificateConverter()
            .setProvider(CertificateSpec.PROVIDER)
            .getCertificate(certHolder)

        // verify the certificate
        caCert.verify(cakeyPair.public)

        return CaMaterial(
            keyPair = cakeyPair,
            cert = caCert,
        )
    }
}

fun x509ToPem(cert: X509Certificate): String = toPem("CERTIFICATE", cert.encoded)
fun privateKeyToPem(key: PrivateKey): String = toPem("PRIVATE KEY", key.encoded)

// Helper function to convert DER byte array to PEM string like:
// -----BEGIN TYPE-----
// BASE64 ENCODED DATA
// -----END TYPE-----
fun toPem(type: String, der: ByteArray): String {
    val writer = StringWriter()
    JcaPEMWriter(writer).use { pem ->
        pem.writeObject(PemObject(type, der))
    }
    return writer.toString()
}
