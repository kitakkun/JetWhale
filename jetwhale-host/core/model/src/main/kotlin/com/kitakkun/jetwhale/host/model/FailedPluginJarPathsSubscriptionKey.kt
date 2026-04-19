package com.kitakkun.jetwhale.host.model

import kotlinx.collections.immutable.ImmutableList
import soil.query.SubscriptionKey

typealias FailedPluginJarPathsSubscriptionKey = SubscriptionKey<ImmutableList<String>>
