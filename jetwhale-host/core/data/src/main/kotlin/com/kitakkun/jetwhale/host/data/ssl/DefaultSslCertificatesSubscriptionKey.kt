package com.kitakkun.jetwhale.host.data.ssl

import com.kitakkun.jetwhale.host.model.SslCertificateEntry
import com.kitakkun.jetwhale.host.model.SslCertificateManager
import com.kitakkun.jetwhale.host.model.SslCertificatesSubscriptionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import soil.query.SubscriptionId
import soil.query.SubscriptionKey
import soil.query.buildSubscriptionKey

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding<SslCertificatesSubscriptionKey>())
class DefaultSslCertificatesSubscriptionKey(
    private val sslCertificateManager: SslCertificateManager,
) : SslCertificatesSubscriptionKey,
    SubscriptionKey<List<SslCertificateEntry>> by buildSubscriptionKey(
        id = SubscriptionId("ssl_certificates"),
        subscribe = { sslCertificateManager.certificatesFlow },
    )
