package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/** Restarts the host through the launcher, which clears the given version's set-aside mark and tries it again. */
interface TryHostVersionAgainMutationKey : MutationKey<Unit, String>
