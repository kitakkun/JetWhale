package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/**
 * Persists whether the host checks the update site on startup. The startup check only
 * notifies; installing an update always requires an explicit user action.
 */
interface CheckForUpdatesOnStartupMutationKey : MutationKey<Unit, Boolean>
