package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/** Cancels the install with the given job id. */
interface CancelPluginInstallMutationKey : MutationKey<Unit, String>
