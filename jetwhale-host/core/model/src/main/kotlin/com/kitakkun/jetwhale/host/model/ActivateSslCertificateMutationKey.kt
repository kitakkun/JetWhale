package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/** Marks the certificate identified by the given id as active. */
interface ActivateSslCertificateMutationKey : MutationKey<Boolean, String>
