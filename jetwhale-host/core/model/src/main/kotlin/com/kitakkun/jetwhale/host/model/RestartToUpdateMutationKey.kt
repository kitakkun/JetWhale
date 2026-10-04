package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/** Restarts the host through the launcher, which then runs the newest installed version. */
interface RestartToUpdateMutationKey : MutationKey<Unit, Unit>
