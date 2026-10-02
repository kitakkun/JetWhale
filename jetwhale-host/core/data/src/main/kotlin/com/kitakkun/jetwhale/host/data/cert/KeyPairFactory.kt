package com.kitakkun.jetwhale.host.data.cert

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security

/**
 * Generates key pairs using the same algorithm/provider as the certificate authority
 * (see [CertificateSpec]) so that issued server certificates stay compatible with the CA chain.
 */
class KeyPairFactory {
    fun generate(): KeyPair {
        if (Security.getProvider(CertificateSpec.PROVIDER) == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        val keyPairGenerator = KeyPairGenerator.getInstance(CertificateSpec.ALGORITHM, CertificateSpec.PROVIDER)
        keyPairGenerator.initialize(256)
        return keyPairGenerator.generateKeyPair()
    }
}
