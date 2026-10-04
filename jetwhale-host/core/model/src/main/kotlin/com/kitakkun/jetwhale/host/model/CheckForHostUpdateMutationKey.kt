package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/** Looks up the newest host release. */
interface CheckForHostUpdateMutationKey : MutationKey<Unit, Unit>
