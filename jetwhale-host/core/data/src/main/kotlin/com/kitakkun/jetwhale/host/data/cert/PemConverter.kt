package com.kitakkun.jetwhale.host.data.cert

import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.util.io.pem.PemObject
import java.io.StringWriter
import java.security.PrivateKey
import java.security.cert.X509Certificate

class PemConverter {
    fun X509Certificate.x509ToPem(): String = this.encoded.toPem("CERTIFICATE")
    fun PrivateKey.toPem(): String = this.encoded.toPem("PRIVATE KEY")

    // Helper function to convert DER byte array to PEM string like:
    // -----BEGIN TYPE-----
    // BASE64 ENCODED DATA
    // -----END TYPE-----
    private fun ByteArray.toPem(type: String): String {
        val writer = StringWriter()
        JcaPEMWriter(writer).use { pem ->
            pem.writeObject(PemObject(type, this))
        }
        return writer.toString()
    }
}
