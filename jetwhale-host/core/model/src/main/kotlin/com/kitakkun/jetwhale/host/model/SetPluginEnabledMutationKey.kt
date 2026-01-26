package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

data class SetPluginEnabledParams(
    val pluginId: String,
    val enabled: Boolean,
)

typealias SetPluginEnabledMutationKey = MutationKey<Unit, SetPluginEnabledParams>
