package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/** Deletes the certificate identified by the given id. */
interface DeleteSslCertificateMutationKey : MutationKey<Boolean, String>
