package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/** Persists the sidebar width, in dp, so the next launch opens with it. */
interface SaveSidebarWidthMutationKey : MutationKey<Unit, Float>
