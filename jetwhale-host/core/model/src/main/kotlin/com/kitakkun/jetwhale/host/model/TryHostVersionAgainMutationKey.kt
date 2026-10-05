package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.release.HostVersion
import soil.query.MutationKey

/** Restarts the host through the launcher, which clears the given version's set-aside mark and tries it again. */
interface TryHostVersionAgainMutationKey : MutationKey<Unit, HostVersion>
