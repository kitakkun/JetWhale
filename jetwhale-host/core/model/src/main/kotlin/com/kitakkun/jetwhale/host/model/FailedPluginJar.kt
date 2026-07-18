package com.kitakkun.jetwhale.host.model

/** A plugin jar the host could not load, with the reason the load failed. */
data class FailedPluginJar(
    val jarPath: String,
    val reason: String,
)
