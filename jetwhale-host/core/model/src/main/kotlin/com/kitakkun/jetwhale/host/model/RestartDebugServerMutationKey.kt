package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/**
 * Restarts the debug WebSocket server with the currently persisted settings. Used to apply
 * certificate changes, which only take effect on the next server start.
 */
interface RestartDebugServerMutationKey : MutationKey<Unit, Unit>
