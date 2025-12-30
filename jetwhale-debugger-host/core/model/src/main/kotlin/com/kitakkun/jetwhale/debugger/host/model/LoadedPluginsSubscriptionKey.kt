package com.kitakkun.jetwhale.debugger.host.model

import kotlinx.collections.immutable.ImmutableList
import soil.query.SubscriptionKey

typealias LoadedPluginsMetaDataSubscriptionKey = SubscriptionKey<ImmutableList<PluginMetaData>>