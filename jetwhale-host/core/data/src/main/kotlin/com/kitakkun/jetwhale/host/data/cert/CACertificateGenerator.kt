package com.kitakkun.jetwhale.host.data.cert

import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.net.InetAddress
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

data class CaMaterial(
    val keyPair: KeyPair,
    val cert: X509Certificate,
)

class CACertificateGenerator {
    /**
     * Creates a self-signed root CA for the local debug PKI.
     *
     * The CA carries a critical Name Constraints extension that permits issuance only for
     * debug-local names (localhost and the private/link-local address ranges). This bounds the blast
     * radius if the CA key ever leaks or the CA is installed into an OS trust store: a conforming
     * validator will reject any certificate this CA signs for a public name (e.g. `example.com`), so
     * the CA cannot be abused to impersonate arbitrary internet hosts.
     *
     * Existing certificates are unaffected — the constraints only apply to CAs generated after this
     * change, so users must generate a new certificate to obtain them.
     */
    fun createRootCA(
        commonName: String,
        daysValid: Int = 3650,
    ): CaMaterial {
        if (Security.getProvider(CertificateSpec.PROVIDER) == null) {
            Security.addProvider(BouncyCastleProvider())
        }

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
            Extension.basicConstraints,
            true,
            BasicConstraints(true),
        ).addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign),
        ).addExtension(
            Extension.subjectKeyIdentifier,
            false,
            JcaX509ExtensionUtils().createSubjectKeyIdentifier(cakeyPair.public),
        ).addExtension(
            // Critical, so a validator that does not understand Name Constraints rejects the chain
            // rather than ignoring the restriction.
            Extension.nameConstraints,
            true,
            localNameConstraints(),
        ).build(
            JcaContentSignerBuilder(CertificateSpec.SIGNATURE_ALGORITHM)
                .setProvider(CertificateSpec.PROVIDER)
                .build(cakeyPair.private),
        )

        val caCert = JcaX509CertificateConverter()
            .setProvider(CertificateSpec.PROVIDER)
            .getCertificate(certHolder)

        caCert.verify(cakeyPair.public)

        return CaMaterial(
            keyPair = cakeyPair,
            cert = caCert,
        )
    }

    /**
     * Permitted subtrees limiting this CA to debug-local names: `localhost` plus the IPv4
     * private/loopback/link-local ranges and IPv6 loopback. iPAddress subtrees are encoded per
     * RFC 5280 §4.2.1.10 as the address octets followed by the netmask octets.
     */
    private fun localNameConstraints(): NameConstraints {
        val permitted = arrayOf(
            GeneralSubtree(GeneralName(GeneralName.dNSName, "localhost")),
            ipv4Subtree("127.0.0.0", 8),
            ipv4Subtree("10.0.0.0", 8),
            ipv4Subtree("172.16.0.0", 12),
            ipv4Subtree("192.168.0.0", 16),
            ipv4Subtree("169.254.0.0", 16),
            ipv6Subtree("::1", 128),
        )
        return NameConstraints(permitted, null)
    }

    private fun ipv4Subtree(address: String, prefixLength: Int): GeneralSubtree {
        val addr = address.split(".").map { it.toInt().toByte() }.toByteArray()
        return ipSubtree(addr, prefixLength)
    }

    private fun ipv6Subtree(address: String, prefixLength: Int): GeneralSubtree {
        val addr = InetAddress.getByName(address).address
        return ipSubtree(addr, prefixLength)
    }

    private fun ipSubtree(addr: ByteArray, prefixLength: Int): GeneralSubtree {
        val mask = ByteArray(addr.size)
        var remaining = prefixLength
        for (i in mask.indices) {
            val bits = minOf(8, maxOf(0, remaining))
            mask[i] = if (bits == 0) 0 else (0xFF shl (8 - bits) and 0xFF).toByte()
            remaining -= 8
        }
        return GeneralSubtree(GeneralName(GeneralName.iPAddress, DEROctetString(addr + mask)))
    }
}
