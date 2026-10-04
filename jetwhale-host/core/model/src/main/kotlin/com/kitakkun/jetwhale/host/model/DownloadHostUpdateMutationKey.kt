package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/** Starts downloading the host release the last check found. */
interface DownloadHostUpdateMutationKey : MutationKey<Unit, Unit>
