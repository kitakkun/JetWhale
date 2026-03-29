package com.kitakkun.jetwhale.host.data.cert

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

class ServerCertificateIssuer {
    fun issue(
        ca: CaMaterial,
        serverKeyPair: KeyPair,
        commonName: String = "localhost",
        dnsSans: List<String> = listOf("localhost"),
        ipSans: List<String> = listOf("127.0.0.1"),
        daysValid: Int = 365,
    ): X509Certificate {
        val now = Date()
        val expiredAt = Date(now.time + daysValid * 24L * 60L * 60L * 1000L)
        val serial = BigInteger(160, SecureRandom()).abs()

        val subject = X500Name("CN=$commonName")
        val issuer = X500Name(ca.cert.subjectX500Principal.name)

        val sanNames = dnsSans.map { GeneralName(GeneralName.dNSName, it) } + ipSans.map { GeneralName(GeneralName.iPAddress, it) }
        val subjectAllNames = GeneralNames(sanNames.toTypedArray())

        val extUtils = JcaX509ExtensionUtils()

        val builder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            now,
            expiredAt,
            subject,
            serverKeyPair.public,
        ).addExtension(
            // mark this certificate is not a CA
            Extension.basicConstraints,
            true,
            BasicConstraints(false),
        ).addExtension(
            // digitalSignature: for TLS handshake
            // keyEncipherment: mainly for RSA key transport; kept for compatibility
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment),
        ).addExtension(
            // assert this certificate is used for TLS server auth
            Extension.extendedKeyUsage,
            false,
            ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth),
        ).addExtension(
            // required for hostname verification (CN is ignored by modern TLS)
            Extension.subjectAlternativeName,
            false,
            subjectAllNames,
        ).addExtension(
            // provide public key hash for identifying certificate
            Extension.subjectKeyIdentifier,
            false,
            extUtils.createSubjectKeyIdentifier(serverKeyPair.public),
        ).addExtension(
            // identify issuer (CA) key for chain building
            Extension.authorityKeyIdentifier,
            false,
            extUtils.createAuthorityKeyIdentifier(ca.cert),
        )

        val signer = JcaContentSignerBuilder(CertificateSpec.SIGNATURE_ALGORITHM)
            .setProvider(CertificateSpec.PROVIDER)
            .build(ca.keyPair.private)

        val certHolder = builder.build(signer)
        val cert = JcaX509CertificateConverter()
            .setProvider(CertificateSpec.PROVIDER)
            .getCertificate(certHolder)

        cert.verify(ca.cert.publicKey)

        return cert
    }
}
