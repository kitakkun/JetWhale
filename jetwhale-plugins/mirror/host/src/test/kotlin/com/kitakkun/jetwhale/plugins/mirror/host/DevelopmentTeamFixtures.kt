package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** A team setting with no team, and no storage to read one from. */
internal fun emptyDevelopmentTeamSetting() = DevelopmentTeamSetting(storage = null, scope = CoroutineScope(Dispatchers.Unconfined), signingTeam = RunnerSigningTeam())
