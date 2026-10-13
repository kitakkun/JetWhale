package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/**
 * Queues an install and returns its job at once. The install itself runs in [PluginInstallJobService], so a
 * mutation torn down with its screen cannot cancel a download.
 */
typealias StartPluginInstallMutationKey = MutationKey<PluginInstallJob, PluginInstallRequest>
