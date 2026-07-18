package com.kitakkun.jetwhale.host.data.ssl

import com.kitakkun.jetwhale.host.model.DeleteSslCertificateMutationKey
import com.kitakkun.jetwhale.host.model.SslCertificateManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class, binding<DeleteSslCertificateMutationKey>())
@Inject
class DefaultDeleteSslCertificateMutationKey(
    private val sslCertificateManager: SslCertificateManager,
) : DeleteSslCertificateMutationKey,
    MutationKey<Boolean, String> by buildMutationKey(
        id = MutationId("delete_ssl_certificate"),
        mutate = { id: String ->
            // File removal and metadata persistence are blocking disk work.
            withContext(Dispatchers.IO) {
                sslCertificateManager.deleteCertificate(id)
            }
        },
    )
