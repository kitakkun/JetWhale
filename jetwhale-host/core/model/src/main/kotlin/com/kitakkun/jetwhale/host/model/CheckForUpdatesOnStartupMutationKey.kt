package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/** Persists whether the host looks for a newer release when it starts. The check only notifies; a download always waits for the user. */
interface CheckForUpdatesOnStartupMutationKey : MutationKey<Unit, Boolean>
