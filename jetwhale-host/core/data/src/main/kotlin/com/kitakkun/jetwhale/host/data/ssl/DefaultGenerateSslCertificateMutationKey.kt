package com.kitakkun.jetwhale.host.data.ssl

import com.kitakkun.jetwhale.host.model.GenerateSslCertificateMutationKey
import com.kitakkun.jetwhale.host.model.SslCertificateEntry
import com.kitakkun.jetwhale.host.model.SslCertificateManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class, binding<GenerateSslCertificateMutationKey>())
@Inject
class DefaultGenerateSslCertificateMutationKey(
    private val sslCertificateManager: SslCertificateManager,
) : GenerateSslCertificateMutationKey,
    MutationKey<SslCertificateEntry, String?> by buildMutationKey(
        id = MutationId("generate_ssl_certificate"),
        mutate = { name: String? ->
            sslCertificateManager.generateAndAddCertificate(name)
        },
    )
