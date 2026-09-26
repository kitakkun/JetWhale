package com.kitakkun.jetwhale.host.model

import kotlinx.collections.immutable.ImmutableList
import soil.query.SubscriptionKey

typealias PluginInstallJobsSubscriptionKey = SubscriptionKey<ImmutableList<PluginInstallJob>>
