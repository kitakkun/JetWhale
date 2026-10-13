package com.kitakkun.jetwhale.host.data.cert

import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.util.io.pem.PemObject
import java.io.StringWriter
import java.security.PrivateKey
import java.security.cert.X509Certificate

class PemConverter {
    fun X509Certificate.toPem(): String = this.encoded.toPem("CERTIFICATE")
    fun PrivateKey.toPem(): String = this.encoded.toPem("PRIVATE KEY")

    private fun ByteArray.toPem(type: String): String {
        val writer = StringWriter()
        JcaPEMWriter(writer).use { pem ->
            pem.writeObject(PemObject(type, this))
        }
        return writer.toString()
    }
}
