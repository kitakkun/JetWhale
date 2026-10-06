package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/** Cancels the host download under way. */
interface CancelHostUpdateDownloadMutationKey : MutationKey<Unit, Unit>
