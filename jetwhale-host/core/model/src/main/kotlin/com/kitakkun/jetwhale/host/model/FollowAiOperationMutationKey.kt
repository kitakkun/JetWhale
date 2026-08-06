package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/**
 * Persists whether the main window follows the plugin an AI agent is operating.
 */
interface FollowAiOperationMutationKey : MutationKey<Unit, Boolean>
