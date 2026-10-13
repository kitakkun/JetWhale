package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/** Forgets the finished install with the given job id. */
interface DismissPluginInstallMutationKey : MutationKey<Unit, String>
