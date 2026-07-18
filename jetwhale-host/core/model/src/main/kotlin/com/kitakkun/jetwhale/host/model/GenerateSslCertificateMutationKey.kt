package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/** Generates a new certificate. The parameter is an optional display name. */
interface GenerateSslCertificateMutationKey : MutationKey<SslCertificateEntry, String?>
