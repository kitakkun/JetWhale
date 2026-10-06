package com.kitakkun.jetwhale.host.release

import kotlinx.serialization.Serializable

/**
 * What a running host publishes with [HostVersionsDirectory.publishInstanceJson]: a loopback port where
 * it takes requests to bring its window to the front, its process ID, and the token a
 * [BringToFrontClient] request has to carry.
 */
@Serializable
data class InstanceJson(
    val port: Int,
    val pid: Long,
    val token: String,
)
