package com.kitakkun.jetwhale.host.data.ssl

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.data.FilePermissions
import com.kitakkun.jetwhale.host.data.cert.CACertificateGenerator
import com.kitakkun.jetwhale.host.data.cert.KeyPairFactory
import com.kitakkun.jetwhale.host.data.cert.PemConverter
import com.kitakkun.jetwhale.host.data.cert.ServerCertificateIssuer
import com.kitakkun.jetwhale.host.model.SslCertificateEntry
import com.kitakkun.jetwhale.host.model.SslCertificateManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Serializable
private data class CertificateMetadata(
    val id: String,
    val name: String,
    val createdAt: Long,
    val isActive: Boolean,
)

@Serializable
private data class CertificatesStore(
    val certificates: List<CertificateMetadata> = emptyList(),
)

/**
 * File-backed [SslCertificateManager]. Each entry is materialised as:
 * - `keystore_<id>.p12`: a PKCS#12 keystore holding the server private key and its certificate chain
 *   (server certificate + CA certificate). Consumed by the host to serve wss.
 * - `ca_<id>.pem`: the CA certificate in PEM, exposed as the trust anchor for target apps.
 *
 * Certificates are generated in-process with BouncyCastle (see the `cert` package), so no external
 * tooling such as `keytool` is required.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultSslCertificateManager(
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
) : SslCertificateManager {
    private val json = Json { prettyPrint = true }

    private val mutableCertificatesFlow: MutableStateFlow<List<SslCertificateEntry>> by lazy {
        MutableStateFlow(getAllCertificates())
    }
    override val certificatesFlow: StateFlow<List<SslCertificateEntry>> get() = mutableCertificatesFlow

    private fun notifyCertificatesChanged() {
        mutableCertificatesFlow.value = getAllCertificates()
    }

    private val caCertificateGenerator = CACertificateGenerator()
    private val serverCertificateIssuer = ServerCertificateIssuer()
    private val keyPairFactory = KeyPairFactory()
    private val pemConverter = PemConverter()

    private val sslDir: File
        get() = appDataDirectoryProvider.getSslDirectory()

    private val metadataFile: File
        get() = File(sslDir, "certificates.json")

    private fun keyStoreFile(id: String): File = File(sslDir, "keystore_$id.p12")
    private fun caCertPemFile(id: String): File = File(sslDir, "ca_$id.pem")
    private fun keyAlias(id: String): String = "jetwhale_$id"

    private fun loadMetadata(): CertificatesStore {
        if (!metadataFile.exists()) return CertificatesStore()
        return runCatching { json.decodeFromString<CertificatesStore>(metadataFile.readText()) }
            .getOrDefault(CertificatesStore())
    }

    private fun saveMetadata(store: CertificatesStore) {
        metadataFile.writeText(json.encodeToString(store))
        FilePermissions.restrictToOwnerFile(metadataFile)
    }

    override fun getAllCertificates(): List<SslCertificateEntry> {
        return loadMetadata().certificates.mapNotNull { metadata ->
            val pemFile = caCertPemFile(metadata.id)
            if (!pemFile.exists()) return@mapNotNull null
            SslCertificateEntry(
                id = metadata.id,
                name = metadata.name,
                createdAt = metadata.createdAt,
                caCertificatePem = pemFile.readText(),
                isActive = metadata.isActive,
            )
        }
    }

    override fun getActiveCertificate(): SslCertificateEntry? = getAllCertificates().find { it.isActive }

    override fun hasCertificate(): Boolean = getAllCertificates().isNotEmpty()

    override fun generateAndAddCertificate(name: String?): SslCertificateEntry {
        val id = UUID.randomUUID().toString().take(8)
        val createdAt = System.currentTimeMillis()
        val certName = name ?: generateDefaultName(createdAt)

        // Build a self-contained local PKI: a root CA that signs a server certificate valid for
        // localhost / 127.0.0.1 plus this machine's current LAN addresses, so physical devices on
        // the same network pass hostname verification. LAN addresses are captured at generation
        // time — when the machine's IP changes (e.g. DHCP), generate a new certificate.
        val ca = caCertificateGenerator.createRootCA(commonName = "JetWhale Local CA")
        val serverKeyPair = keyPairFactory.generate()
        val serverCertificate = serverCertificateIssuer.issue(
            ca = ca,
            serverKeyPair = serverKeyPair,
            commonName = "localhost",
            dnsSans = listOf("localhost"),
            ipSans = listOf("127.0.0.1") + collectLocalIpAddresses(),
        )

        // Persist the server material as a PKCS#12 keystore consumed by the wss server.
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry(
                keyAlias(id),
                serverKeyPair.private,
                KEYSTORE_PASSWORD.toCharArray(),
                arrayOf(serverCertificate, ca.cert),
            )
        }
        val keyStoreFile = keyStoreFile(id)
        FileOutputStream(keyStoreFile).use { output ->
            keyStore.store(output, KEYSTORE_PASSWORD.toCharArray())
        }
        // The keystore holds the CA private key: restrict it to owner read/write only (0600).
        FilePermissions.restrictToOwnerFile(keyStoreFile)

        // Persist the CA certificate as the trust anchor distributed to target apps.
        val pemFile = caCertPemFile(id)
        with(pemConverter) {
            pemFile.writeText(ca.cert.toPem())
        }
        FilePermissions.restrictToOwnerFile(pemFile)

        val store = loadMetadata()
        val updatedCertificates = store.certificates.map { it.copy(isActive = false) } +
            CertificateMetadata(
                id = id,
                name = certName,
                createdAt = createdAt,
                isActive = true,
            )
        saveMetadata(CertificatesStore(updatedCertificates))
        notifyCertificatesChanged()

        return SslCertificateEntry(
            id = id,
            name = certName,
            createdAt = createdAt,
            caCertificatePem = caCertPemFile(id).readText(),
            isActive = true,
        )
    }

    override fun setActiveCertificate(id: String): Boolean {
        val store = loadMetadata()
        if (store.certificates.none { it.id == id }) return false
        saveMetadata(CertificatesStore(store.certificates.map { it.copy(isActive = it.id == id) }))
        notifyCertificatesChanged()
        return true
    }

    override fun deleteCertificate(id: String): Boolean {
        val store = loadMetadata()
        val toDelete = store.certificates.find { it.id == id } ?: return false

        keyStoreFile(id).delete()
        caCertPemFile(id).delete()

        val remaining = store.certificates.filter { it.id != id }
        // When the active certificate is removed, promote the first remaining one so the server can
        // still find an active certificate after a restart.
        val updatedCertificates = if (toDelete.isActive && remaining.isNotEmpty()) {
            remaining.mapIndexed { index, cert -> cert.copy(isActive = index == 0) }
        } else {
            remaining
        }
        saveMetadata(CertificatesStore(updatedCertificates))
        notifyCertificatesChanged()
        return true
    }

    override fun getActiveKeyStore(): KeyStore? {
        val active = loadMetadata().certificates.find { it.isActive } ?: return null
        val file = keyStoreFile(active.id)
        if (!file.exists()) return null
        return KeyStore.getInstance("PKCS12").apply {
            FileInputStream(file).use { input -> load(input, KEYSTORE_PASSWORD.toCharArray()) }
        }
    }

    override fun getKeyStorePassword(): CharArray = KEYSTORE_PASSWORD.toCharArray()

    override fun getActiveKeyAlias(): String? {
        val active = loadMetadata().certificates.find { it.isActive } ?: return null
        return keyAlias(active.id)
    }

    /**
     * Returns the machine's non-loopback IPv4 addresses (Wi-Fi/Ethernet), used as additional
     * Subject Alternative Names so LAN clients can verify the certificate against the address they
     * dialed.
     */
    private fun collectLocalIpAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .distinct()
            .toList()
    }.getOrDefault(emptyList())

    private fun generateDefaultName(createdAt: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return "Certificate ${dateFormat.format(Date(createdAt))}"
    }

    companion object {
        // The keystore lives in the user's local app data directory and only protects a locally
        // generated, self-signed key pair, so a fixed password is acceptable.
        private const val KEYSTORE_PASSWORD = "jetwhale_ssl"
    }
}
